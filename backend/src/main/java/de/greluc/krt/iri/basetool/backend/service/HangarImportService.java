package de.greluc.krt.iri.basetool.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.backend.model.Ship;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.FleetviewEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.FleetviewImportResponseDto;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
/**
 * Handles the import of a CCU Game Fleetview JSON export into a user's hangar.
 *
 * <p>Each entry in the JSON is matched against existing {@code ShipType} records via
 * case-insensitive name lookup. Matched entries are imported as new {@code Ship} entities;
 * unmatched entries are collected in a skip-list and returned in the response so the user
 * can take manual corrective action (e.g. trigger a UEX sync first).
 *
 * <p>Duplicate handling: if a {@code ShipType} appears multiple times in the JSON, the import
 * ensures that the user's hangar contains <em>at least</em> as many ships of that type as the
 * JSON specifies. The number of ships to create is {@code max(0, jsonCount - hangarCount)}.
 * Ships already present in excess of the JSON count are <strong>never deleted</strong>.
 *
 * <p>The {@code skippedCount} and {@code duplicateShips} fields in {@link FleetviewImportResponseDto}
 * are reused for ships where the hangar count already meets or exceeds the JSON count (no new
 * ships needed). {@code skippedShips} lists ship names that had no matching {@code ShipType}
 * in the database.
 *
 * <p>Since {@code fleetview.json} carries no insurance information, imported ships receive
 * the neutral default value {@code "0"} which satisfies the pattern constraint and signals
 * "unknown insurance" to the user.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class HangarImportService {

    /** Neutral insurance default used for all imported ships (no insurance data in fleetview.json). */
    static final String DEFAULT_INSURANCE = "0";

    private final ShipRepository shipRepository;
    private final ShipTypeRepository shipTypeRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * Parses a Fleetview JSON file and imports all matched ships into the given user's hangar.
     *
     * <p>The import ensures that after the operation the hangar contains at least as many ships
     * of each type as the JSON specifies. Ships already present are never removed. For each
     * distinct ship type found in the JSON, the number of new ships created equals
     * {@code max(0, jsonCount - hangarCount)}.
     *
     * <p>The JSON entries are aggregated into a {@code Map<UUID, Integer>} (ShipType-ID → count)
     * <em>before</em> any database write takes place, so the loop over distinct ship types
     * performs at most one {@code COUNT} query and at most one {@code INSERT} per type — avoiding
     * N+1 problems and Optimistic-Locking conflicts inside the loop.
     *
     * @param userId user ID from the JWT {@code sub} claim
     * @param file   multipart file containing the CCU Game Fleetview JSON export
     * @return import result with statistics and lists of skipped ship names
     * @throws BadRequestException if the file is empty or cannot be parsed as JSON
     * @throws NotFoundException   if the user is not found
     */
    @Transactional
    public @NotNull FleetviewImportResponseDto importFleetview(@NotNull UUID userId, @NotNull MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("The uploaded file is empty.");
        }

        List<FleetviewEntryDto> entries = parseJson(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        List<String> skippedShips = new ArrayList<>();

        // --- Phase 1: Resolve ShipTypes and aggregate counts per distinct ShipType ---
        // Map: ShipType → desired count in JSON (order preserved for deterministic behaviour)
        Map<ShipType, Integer> jsonCountByType = new LinkedHashMap<>();
        // Map: ShipType.getId() → first matched entry (for individual ship name lookup)
        Map<UUID, FleetviewEntryDto> firstEntryByTypeId = new LinkedHashMap<>();

        for (FleetviewEntryDto entry : entries) {
            if (entry.name() == null || entry.name().isBlank()) {
                log.warn("Fleetview import: skipping entry with blank name for user {}", userId);
                continue;
            }

            var shipTypeOpt = shipTypeRepository.findByNameIgnoreCase(entry.name().trim());
            if (shipTypeOpt.isEmpty()) {
                log.debug("Fleetview import: no ShipType match for '{}' (user {})", entry.name(), userId);
                // Only add to skipped list once per name to avoid duplicating error messages
                if (skippedShips.stream().noneMatch(s -> s.equalsIgnoreCase(entry.name().trim()))) {
                    skippedShips.add(entry.name());
                }
                continue;
            }

            // The isEmpty()/continue guard above already excludes the empty case, but
            // orElseThrow makes that contract explicit to the reader (and silences the
            // "Optional.get() without isPresent" warning from SpotBugs / SonarLint).
            ShipType shipType = shipTypeOpt.orElseThrow();
            jsonCountByType.merge(shipType, 1, Integer::sum);
            firstEntryByTypeId.putIfAbsent(shipType.getId(), entry);
        }

        // --- Phase 2: For each distinct ShipType, create missing ships ---
        int importedCount = 0;
        int alreadySufficientCount = 0; // ships not imported because hangar count >= JSON count

        for (Map.Entry<ShipType, Integer> entry : jsonCountByType.entrySet()) {
            ShipType shipType = entry.getKey();
            int jsonCount = entry.getValue();

            long hangarCount = shipRepository.countByOwnerIdAndShipTypeId(userId, shipType.getId());
            int toCreate = (int) Math.max(0L, jsonCount - hangarCount);

            if (toCreate > 0) {
                FleetviewEntryDto firstEntry = firstEntryByTypeId.get(shipType.getId());
                String individualName = (firstEntry != null
                        && firstEntry.shipname() != null
                        && !firstEntry.shipname().isBlank())
                        ? firstEntry.shipname().trim()
                        : null;

                for (int i = 0; i < toCreate; i++) {
                    Ship ship = new Ship();
                    ship.setOwner(user);
                    ship.setShipType(shipType);
                    ship.setInsurance(DEFAULT_INSURANCE);
                    ship.setFitted(false);
                    // Individual name only set on first ship; subsequent duplicates get null
                    ship.setName(i == 0 ? individualName : null);
                    shipRepository.save(ship);
                }

                log.info("Fleetview import: created {} ship(s) of type '{}' for user {} (jsonCount={}, hangarCount={})",
                        toCreate, shipType.getName(), userId, jsonCount, hangarCount);
                importedCount += toCreate;
            } else {
                log.debug("Fleetview import: hangar already has {} ship(s) of type '{}', JSON requests {} — skipping (user {})",
                        hangarCount, shipType.getName(), jsonCount, userId);
                alreadySufficientCount += jsonCount;
            }
        }

        log.info("Fleetview import for user {}: imported={}, alreadySufficient={}, skipped={}",
                userId, importedCount, alreadySufficientCount, skippedShips.size());

        return new FleetviewImportResponseDto(
                importedCount,
                skippedShips.size(),
                alreadySufficientCount,
                skippedShips,
                List.of()
        );
    }

    private @NotNull List<FleetviewEntryDto> parseJson(@NotNull MultipartFile file) {
        try {
            return objectMapper.readValue(
                    file.getInputStream(),
                    new TypeReference<List<FleetviewEntryDto>>() {}
            );
        } catch (IOException e) {
            log.warn("Fleetview import: failed to parse JSON — {}", e.getMessage());
            throw new BadRequestException("The uploaded file could not be parsed as a valid Fleetview JSON.");
        }
    }
}
