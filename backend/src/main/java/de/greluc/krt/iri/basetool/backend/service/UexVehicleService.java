package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexVehicleDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.GameItemSourceSystem;
import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.repository.ManufacturerRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * R2 UEX vehicle sync. Replaces the pre-R2 name-only matcher with the §8.5 UUID-first chain and
 * populates the 36 capability flags / dimensions / fuel / urls / English description that the V111
 * migration added to {@code ship_type}.
 *
 * <p>Resolution chain per SC_WIKI_SYNC_PLAN.md §8.5:
 *
 * <ol>
 *   <li>{@code findByExternalUuid(dto.uuid)} — strongest signal (Plan §3.6's 241-test invariant).
 *   <li>{@code findByUexVehicleId(dto.id)} — picks up rows the previous sync stamped without a UUID
 *       (~31% of UEX vehicles have no UUID).
 *   <li>{@code findByNameIgnoreCase(dto.name)} — legacy fallback that <b>backfills both</b> {@code
 *       external_uuid} (when UEX provides one) and {@code uex_vehicle_id} on hit. This is R2's
 *       substitute for the planned V112 data migration — the first sync after R2 deploys sets both
 *       columns on every match by name, and subsequent syncs never re-enter this path.
 *   <li>create a new row stamped {@link GameItemSourceSystem#UEX_ONLY}.
 * </ol>
 *
 * <p>The legacy synthesized {@code description} text is still written so pre-R2 UIs render
 * something while consumers migrate to {@code description_en}.
 *
 * <p>Empty UEX response short-circuits without wiping local data. Orphan handling via {@link
 * ShipTypeRepository#markUexDeletedExcept(java.util.Collection, Instant)} gated on a non-empty
 * seen-id set.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UexVehicleService {

  private final UexClient uexClient;
  private final ShipTypeRepository shipTypeRepository;
  private final ManufacturerRepository manufacturerRepository;

  /** Pulls the UEX vehicle catalogue and upserts each row. */
  @Transactional
  public void syncVehicles() {
    log.info("Starting synchronization of UEX vehicles (ships)...");
    List<UexVehicleDto> vehicles = uexClient.getVehicles();
    if (vehicles.isEmpty()) {
      log.warn("No vehicles received from UEX API. Aborting synchronization.");
      return;
    }

    Instant now = Instant.now();
    Set<Integer> seenUexVehicleIds = new HashSet<>();
    int added = 0;
    int updated = 0;
    int skipped = 0;
    for (UexVehicleDto dto : vehicles) {
      try {
        boolean isNew = upsertVehicle(dto, now, seenUexVehicleIds);
        if (isNew) {
          added++;
        } else {
          updated++;
        }
      } catch (Exception e) {
        log.error("Failed to process UEX vehicle dto: {}", dto, e);
        skipped++;
      }
    }

    if (seenUexVehicleIds.isEmpty()) {
      log.warn("Skipping orphan sweep — no UEX vehicle was processed successfully.");
    } else {
      int marked = shipTypeRepository.markUexDeletedExcept(seenUexVehicleIds, now);
      if (marked > 0) {
        log.info("Marked {} ship_type row(s) uex_deleted (no longer in UEX feed)", marked);
      }
    }

    log.info(
        "Finished UEX vehicle sync: {} added, {} updated, {} skipped", added, updated, skipped);
  }

  /**
   * Upserts a single UEX vehicle DTO into {@code ship_type}.
   *
   * @param dto inbound UEX row
   * @param now timestamp to stamp on the row
   * @param seenUexVehicleIds accumulator for the orphan sweep
   * @return {@code true} when the row was newly inserted
   */
  private boolean upsertVehicle(UexVehicleDto dto, Instant now, Set<Integer> seenUexVehicleIds) {
    if (!StringUtils.hasText(dto.name())) {
      log.debug("Skipping UEX vehicle with missing name: {}", dto);
      return false;
    }

    UUID externalUuid = parseUuid(dto.uuid());

    Optional<ShipType> existingOpt = Optional.empty();
    if (externalUuid != null) {
      existingOpt = shipTypeRepository.findByExternalUuid(externalUuid);
    }
    if (existingOpt.isEmpty() && dto.id() != null) {
      existingOpt = shipTypeRepository.findByUexVehicleId(dto.id());
    }
    if (existingOpt.isEmpty()) {
      existingOpt = shipTypeRepository.findByNameIgnoreCase(dto.name());
    }

    ShipType shipType = existingOpt.orElseGet(ShipType::new);
    boolean isNew = existingOpt.isEmpty();
    if (isNew) {
      shipType.setName(dto.name());
      shipType.setSourceSystems(GameItemSourceSystem.UEX_ONLY);
    }

    // Backfill the cross-source keys. The legacy name-fallback rows get external_uuid +
    // uex_vehicle_id
    // on this run; subsequent syncs never re-enter the name path.
    if (externalUuid != null && shipType.getExternalUuid() == null) {
      shipType.setExternalUuid(externalUuid);
    }
    if (dto.id() != null) {
      shipType.setUexVehicleId(dto.id());
      seenUexVehicleIds.add(dto.id());
    }

    Manufacturer manufacturer = resolveManufacturer(dto);
    if (manufacturer != null) {
      shipType.setManufacturer(manufacturer);
    }

    applyVehicleFields(shipType, dto);

    // Synthesised description for back-compat with pre-R2 UI consumers; description_en
    // becomes the authoritative source from R2 onwards.
    shipType.setDescription(buildLegacyDescription(dto));

    shipType.setUexSyncedAt(now);
    shipType.setUexDeletedAt(null);
    // Promote UEX_ONLY -> BOTH when Wiki already wrote this row (R4+).
    if (shipType.getSourceSystems() == GameItemSourceSystem.WIKI_ONLY) {
      shipType.setSourceSystems(GameItemSourceSystem.BOTH);
    }

    shipTypeRepository.save(shipType);
    return isNew;
  }

  /**
   * Copies every R2 column from the DTO onto the entity. Defensive: every setter receives the DTO
   * value (possibly {@code null}) so a missing field on the UEX side clears the local row to {@code
   * null} instead of stale-write surviving across schema migrations.
   *
   * @param shipType local row being updated
   * @param dto inbound DTO
   */
  private static void applyVehicleFields(ShipType shipType, UexVehicleDto dto) {
    shipType.setUexSlug(dto.slug());
    shipType.setNameFull(dto.nameFull());
    shipType.setScu(dto.scu());
    shipType.setCrewMin(dto.crewMin());
    shipType.setCrewMax(dto.crewMax());
    shipType.setMass(dto.mass());
    shipType.setMassTotal(dto.massTotal());
    shipType.setWidth(dto.width());
    shipType.setHeight(dto.height());
    shipType.setLengthM(dto.length());
    shipType.setPadType(dto.padType());
    shipType.setFuelQuantum(dto.fuelQuantum());
    shipType.setFuelHydrogen(dto.fuelHydrogen());
    shipType.setVehicleInventoryScu(dto.vehicleInventory());
    shipType.setOreCapacity(dto.oreCapacity());
    shipType.setContainerSizes(dto.containerSizes());
    shipType.setMaxMedicalTier(dto.maxMedicalTier());
    shipType.setHealth(dto.health());
    shipType.setShieldHp(dto.shieldHp());
    shipType.setUrlStore(dto.urlStore());
    shipType.setUrlBrochure(dto.urlBrochure());
    shipType.setUrlHotsite(dto.urlHotsite());
    shipType.setUrlPhoto(dto.urlPhoto());
    shipType.setUrlVideo(dto.urlVideo());
    shipType.setUrlWiki(dto.urlWiki());
    shipType.setDescriptionEn(dto.descriptionEn());
    // descriptionDe stays from a prior R4 Wiki sync; UEX does not expose a DE description.

    shipType.setIsAddon(asBoolean(dto.isAddon()));
    shipType.setIsBoarding(asBoolean(dto.isBoarding()));
    shipType.setIsBomber(asBoolean(dto.isBomber()));
    shipType.setIsCargo(asBoolean(dto.isCargo()));
    shipType.setIsCarrier(asBoolean(dto.isCarrier()));
    shipType.setIsCivilian(asBoolean(dto.isCivilian()));
    shipType.setIsConcept(asBoolean(dto.isConcept()));
    shipType.setIsConstruction(asBoolean(dto.isConstruction()));
    shipType.setIsDatarunner(asBoolean(dto.isDatarunner()));
    shipType.setIsDocking(asBoolean(dto.isDocking()));
    shipType.setIsEmp(asBoolean(dto.isEmp()));
    shipType.setIsExploration(asBoolean(dto.isExploration()));
    shipType.setIsGroundVehicle(asBoolean(dto.isGroundVehicle()));
    shipType.setIsHangar(asBoolean(dto.isHangar()));
    shipType.setIsIndustrial(asBoolean(dto.isIndustrial()));
    shipType.setIsInterdiction(asBoolean(dto.isInterdiction()));
    shipType.setIsLoadingDock(asBoolean(dto.isLoadingDock()));
    shipType.setIsMedical(asBoolean(dto.isMedical()));
    shipType.setIsMilitary(asBoolean(dto.isMilitary()));
    shipType.setIsMining(asBoolean(dto.isMining()));
    shipType.setIsPassenger(asBoolean(dto.isPassenger()));
    shipType.setIsQed(asBoolean(dto.isQed()));
    shipType.setIsQuantumCapable(asBoolean(dto.isQuantumCapable()));
    shipType.setIsRacing(asBoolean(dto.isRacing()));
    shipType.setIsRefinery(asBoolean(dto.isRefinery()));
    shipType.setIsRefuel(asBoolean(dto.isRefuel()));
    shipType.setIsRepair(asBoolean(dto.isRepair()));
    shipType.setIsResearch(asBoolean(dto.isResearch()));
    shipType.setIsSalvage(asBoolean(dto.isSalvage()));
    shipType.setIsScanning(asBoolean(dto.isScanning()));
    shipType.setIsScience(asBoolean(dto.isScience()));
    shipType.setIsShowdownWinner(asBoolean(dto.isShowdownWinner()));
    shipType.setIsSpaceship(asBoolean(dto.isSpaceship()));
    shipType.setIsStarter(asBoolean(dto.isStarter()));
    shipType.setIsStealth(asBoolean(dto.isStealth()));
    shipType.setIsTractorBeam(asBoolean(dto.isTractorBeam()));
  }

  /**
   * Looks up the local manufacturer row from the inbound vehicle DTO. Falls back to NULL if no
   * match — the linked manufacturer stays whatever the previous sync set.
   *
   * @param dto inbound vehicle row
   * @return resolved manufacturer, or {@code null}
   */
  private Manufacturer resolveManufacturer(UexVehicleDto dto) {
    if (!StringUtils.hasText(dto.companyName())) {
      return null;
    }
    return manufacturerRepository.findByNameIgnoreCase(dto.companyName()).orElse(null);
  }

  /**
   * Synthesises the legacy multi-line {@code description} (name_full / SCU / crew / wiki|store).
   * Kept for back-compat with pre-R2 UIs; consumers migrate to {@code description_en} over R2's
   * soak window and the column is dropped in R9.
   *
   * @param dto inbound vehicle row
   * @return synthesised text, or {@code null} when no field was usable
   */
  private static String buildLegacyDescription(UexVehicleDto dto) {
    StringBuilder description = new StringBuilder();
    if (StringUtils.hasText(dto.nameFull())) {
      description.append("Full Name: ").append(dto.nameFull()).append("\n");
    }
    if (dto.scu() != null) {
      description.append("SCU: ").append(dto.scu()).append("\n");
    }
    if (StringUtils.hasText(dto.crew())) {
      description.append("Crew: ").append(dto.crew()).append("\n");
    }
    if (StringUtils.hasText(dto.urlWiki())) {
      description.append("Wiki: ").append(dto.urlWiki()).append("\n");
    } else if (StringUtils.hasText(dto.urlStore())) {
      description.append("Store: ").append(dto.urlStore()).append("\n");
    }
    return description.isEmpty() ? null : description.toString().trim();
  }

  /**
   * Parses a UEX-emitted UUID string. UEX returns an empty string for ~31% of vehicles (concepts,
   * capital ships, retired variants). Returns {@code null} on empty / malformed input.
   *
   * @param raw raw UUID string from the DTO
   * @return parsed UUID, or {@code null}
   */
  private static UUID parseUuid(String raw) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException e) {
      log.debug("Skipping malformed UEX uuid '{}': {}", raw, e.getMessage());
      return null;
    }
  }

  /**
   * Normalises UEX's integer 0/1 flag into a {@link Boolean}. Distinguishes between {@code null}
   * (UEX didn't carry the flag) and {@code false} (UEX carried 0).
   *
   * @param flag UEX-style integer
   * @return {@code true} / {@code false} / {@code null}
   */
  private static Boolean asBoolean(Integer flag) {
    if (flag == null) {
      return null;
    }
    return flag == 1;
  }
}
