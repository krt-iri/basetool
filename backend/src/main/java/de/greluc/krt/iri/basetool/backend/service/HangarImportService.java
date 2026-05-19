package de.greluc.krt.iri.basetool.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.Ship;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.FleetviewEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.FleetviewImportResponseDto;
import de.greluc.krt.iri.basetool.backend.model.dto.ShiplistEntryDto;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Handles the import of a third-party ship-export JSON into a user's hangar.
 *
 * <p>Two upload formats are accepted and auto-detected from the first JSON element:
 *
 * <ul>
 *   <li><strong>CCU Game Fleetview</strong> — a flat array of {@code {"name", "shipname", "type"}}
 *       records. No insurance data is carried, so imported ships fall back to the neutral {@link
 *       #DEFAULT_INSURANCE} value.
 *   <li><strong>HangarXPLOR Shiplist</strong> (see {@code https://github.com/dolkensp/HangarXPLOR})
 *       — a richer array of pledge-aware records. Only {@code name}, {@code ship_name}, {@code
 *       entity_type} and {@code lti} are consumed. {@code lti=true} populates the imported ship's
 *       insurance with {@code "LTI"}; {@code lti=false} falls back to {@link #DEFAULT_INSURANCE}
 *       (we know it is not lifetime, but we do not know the actual month count). Entries with
 *       {@code entity_type} other than {@code "ship"} are skipped defensively so that HangarXPLOR
 *       module/package rows never become ships.
 * </ul>
 *
 * <p>After format-specific parsing the two payloads are unified into a single internal {@link
 * FleetImportEntry} stream, and the rest of the import is format-agnostic.
 *
 * <p>Each entry is then resolved against existing {@code ShipType} rows through a four-stage
 * tolerant lookup that maximises matches against UEX's canonical naming (the actual content of the
 * {@code ship_type} table is whatever {@code UexVehicleService} last pulled from {@code
 * https://api.uexcorp.space/2.0/vehicles}):
 *
 * <ol>
 *   <li><strong>Exact case-insensitive match</strong> on {@code ShipType.name} (e.g. {@code "L-21
 *       Wolf"} matches {@code "L-21 Wolf"} regardless of casing).
 *   <li><strong>Normalised match</strong> — both names are folded to lowercase and stripped of
 *       every non-alphanumeric character before comparison. Absorbs hyphen/whitespace drift such as
 *       Fleetview {@code "L21 Wolf"} ↔ UEX {@code "L-21 Wolf"}, {@code "Cyclone-AA"} ↔ UEX {@code
 *       "Cyclone AA"} and HangarXPLOR's {@code "A.T.L.S."} ↔ UEX {@code "ATLS"}.
 *   <li><strong>fv-tokens ⊆ uex-tokens, uniquely</strong> — the entry is a strict abbreviation of a
 *       UEX vehicle (UEX adds marketing-style suffixes that the export omits, like {@code
 *       "Starlifter"}, {@code "Starfighter"}, {@code "Star Runner"}, {@code "Tank"}, {@code
 *       "Rescue"} or the {@code "Mk I"} default-variant tag). Token-<em>set</em> comparison absorbs
 *       word-order drift such as HangarXPLOR's {@code "Hercules Starlifter A2"} ↔ UEX {@code "A2
 *       Hercules Starlifter"}. If the fv-token-set matches more than one UEX vehicle (e.g. {@code
 *       "F7C-M Super Hornet"} would match {@code "Mk I"} / {@code "Heartseeker Mk I"} / {@code "Mk
 *       II"}), the entry is left unresolved to avoid guessing.
 *   <li><strong>uex-tokens ⊆ fv-tokens, uniquely</strong> — the reverse direction, for cases where
 *       the export uses a longer marketing name than UEX's canonical short name (e.g. {@code "Ursa
 *       Rover"} → UEX {@code "Ursa"}, {@code "325a Fighter"} → UEX {@code "325a"}).
 * </ol>
 *
 * <p>All four stages are powered by a single {@code shipTypeRepository.findAll()} call up front, so
 * per-entry resolution is in-memory rather than a database round-trip — there are no N+1 queries
 * even for fleet exports with hundreds of entries.
 *
 * <p>Unmatched entries are collected in the response's {@code skippedShips} list (deduplicated
 * case-insensitively, original casing preserved) so the user can see exactly which ships still need
 * manual correction or a fresh UEX sync.
 *
 * <p>Duplicate handling: if a {@code ShipType} appears multiple times in the upload, the import
 * ensures that the user's hangar contains <em>at least</em> as many ships of that type as the
 * upload specifies. The number of ships to create is {@code max(0, jsonCount - hangarCount)}. Ships
 * already present in excess of the upload count are <strong>never deleted</strong>. The {@code
 * duplicateCount} field in {@link FleetviewImportResponseDto} reports the number of entries that
 * resolved to a {@code ShipType} where the hangar count already met or exceeded the upload count
 * (no new ships needed).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class HangarImportService {

  /**
   * Neutral insurance default used for imported ships when no explicit insurance information is
   * carried by the upload (Fleetview always, Shiplist when {@code lti=false} or absent).
   */
  static final String DEFAULT_INSURANCE = "0";

  /**
   * Insurance string set on imported ships when the HangarXPLOR record carries {@code lti=true}.
   */
  static final String LTI_INSURANCE = "LTI";

  /** Splits a name into alphanumeric tokens. Pre-compiled so the regex is reused per call. */
  private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9]+");

  /** Strips everything outside {@code [a-z0-9]} for the normalised match form. */
  private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]");

  private final ShipRepository shipRepository;
  private final ShipTypeRepository shipTypeRepository;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;

  /**
   * Parses an uploaded ship-export JSON file (Fleetview or HangarXPLOR Shiplist) and imports all
   * resolvable ships into the given user's hangar.
   *
   * <p>The matcher walks four progressively-looser stages (exact case-insensitive, normalised,
   * fv-tokens-subset-of-uex, uex-tokens-subset-of-fv) — each later stage runs only if the earlier
   * ones produced no hit. Stages 3 and 4 require the candidate to be <em>unique</em>; ambiguity
   * leaves the entry unresolved on purpose so the user must explicitly disambiguate (e.g. choose Mk
   * I vs Mk II) instead of having the import guess.
   *
   * <p>The import then ensures that after the operation the hangar contains at least as many ships
   * of each type as the upload specifies; ships already present are never removed. For each
   * distinct {@code ShipType} resolved from the upload the number of new ships created equals
   * {@code max(0, jsonCount - hangarCount)}. Entries that could not be resolved against any {@code
   * ShipType} are surfaced through the response's {@code skippedShips} list so the caller can act
   * on them.
   *
   * @param userId user ID from the JWT {@code sub} claim
   * @param file multipart file containing a CCU Game Fleetview or HangarXPLOR Shiplist JSON export
   * @return import result with statistics and the deduplicated list of unmatched ship names
   * @throws BadRequestException if the file is empty, not parseable as JSON, or in an unknown
   *     format
   * @throws NotFoundException if the user is not found
   */
  @Transactional
  public @NotNull FleetviewImportResponseDto importShips(
      @NotNull UUID userId, @NotNull MultipartFile file) {
    if (file.isEmpty()) {
      throw new BadRequestException("The uploaded file is empty.");
    }

    List<FleetImportEntry> entries = parseEntries(file);

    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));

    // Phase 0: one query to build the tolerant lookup index covering all four match stages.
    ShipTypeIndex index = buildShipTypeIndex();

    // Phase 1: resolve every entry and aggregate per-type counts.
    Set<String> seenSkipped = new HashSet<>();
    List<String> skippedShips = new ArrayList<>();
    Map<UUID, Integer> uploadCountByTypeId = new LinkedHashMap<>();
    Map<UUID, ShipType> shipTypeById = new LinkedHashMap<>();
    Map<UUID, FleetImportEntry> firstEntryByTypeId = new LinkedHashMap<>();

    for (FleetImportEntry entry : entries) {
      String trimmed = entry.name().trim();
      ShipType match = resolveShipType(index, trimmed);

      if (match == null) {
        log.debug("Hangar import: no ShipType match for '{}' (user {})", entry.name(), userId);
        if (seenSkipped.add(trimmed.toLowerCase(Locale.ROOT))) {
          skippedShips.add(entry.name());
        }
        continue;
      }

      UUID typeId = match.getId();
      uploadCountByTypeId.merge(typeId, 1, Integer::sum);
      shipTypeById.putIfAbsent(typeId, match);
      firstEntryByTypeId.putIfAbsent(typeId, entry);
    }

    // Phase 2: for each distinct ShipType, create only the missing ships.
    int importedCount = 0;
    int alreadySufficientCount = 0;

    for (Map.Entry<UUID, Integer> e : uploadCountByTypeId.entrySet()) {
      UUID typeId = e.getKey();
      ShipType shipType = shipTypeById.get(typeId);
      int jsonCount = e.getValue();
      long hangarCount = shipRepository.countByOwnerIdAndShipTypeId(userId, typeId);
      int toCreate = (int) Math.max(0L, jsonCount - hangarCount);

      if (toCreate > 0) {
        FleetImportEntry firstEntry = firstEntryByTypeId.get(typeId);
        String individualName = firstEntry != null ? firstEntry.individualName() : null;
        String insurance =
            (firstEntry != null && firstEntry.insurance() != null)
                ? firstEntry.insurance()
                : DEFAULT_INSURANCE;

        for (int i = 0; i < toCreate; i++) {
          Ship ship = new Ship();
          ship.setOwner(user);
          // Stamp owning squadron from the importer's home squadron — same contract as
          // HangarService.createShip uses for the manual create path. Phase 7's V89 makes
          // `ship.owning_squadron_id` NOT NULL, so the import path must populate it
          // explicitly; the previous nullable column hid this missing-stamp bug for the
          // entire Phase 6 release window.
          ship.setOwningSquadron(user.getSquadron());
          ship.setShipType(shipType);
          ship.setInsurance(insurance);
          ship.setFitted(false);
          ship.setName(i == 0 ? individualName : null);
          shipRepository.save(ship);
        }

        log.info(
            "Hangar import: created {} ship(s) of type '{}' for user {} (jsonCount={},"
                + " hangarCount={})",
            toCreate,
            shipType.getName(),
            userId,
            jsonCount,
            hangarCount);
        importedCount += toCreate;
      } else {
        log.debug(
            "Hangar import: hangar already has {} ship(s) of type '{}', upload requests {} —"
                + " skipping (user {})",
            hangarCount,
            shipType.getName(),
            jsonCount,
            userId);
        alreadySufficientCount += jsonCount;
      }
    }

    log.info(
        "Hangar import for user {}: imported={}, alreadySufficient={}, skipped={}",
        userId,
        importedCount,
        alreadySufficientCount,
        skippedShips.size());

    return new FleetviewImportResponseDto(
        importedCount, skippedShips.size(), alreadySufficientCount, skippedShips, List.of());
  }

  /**
   * Reads the multipart body, sniffs the format from the first array element, and converts every
   * record into the format-agnostic {@link FleetImportEntry}. The probe inspects field names rather
   * than values: a {@code pledge_id} or {@code ship_code} key flags HangarXPLOR Shiplist; a {@code
   * shipname} or {@code type} key flags CCU Game Fleetview. Records with a blank {@code name} field
   * or (for Shiplist) an {@code entity_type} other than {@code "ship"} are silently dropped — they
   * never reach the resolver.
   *
   * @param file multipart upload from the controller
   * @return parsed entries (possibly empty); never {@code null}
   * @throws BadRequestException if the JSON is malformed, the root is not an array, or no field on
   *     the first element matches either known format
   */
  private @NotNull List<FleetImportEntry> parseEntries(@NotNull MultipartFile file) {
    JsonNode root;
    try {
      root = objectMapper.readTree(file.getInputStream());
    } catch (IOException e) {
      log.warn("Hangar import: failed to parse JSON — {}", e.getMessage());
      throw new BadRequestException(
          "The uploaded file could not be parsed as a valid ship-list JSON.");
    }

    if (root == null || !root.isArray()) {
      throw new BadRequestException("The uploaded file must contain a JSON array at the root.");
    }
    if (root.isEmpty()) {
      return List.of();
    }

    JsonNode probe = null;
    for (JsonNode el : root) {
      if (el != null && el.isObject()) {
        probe = el;
        break;
      }
    }
    if (probe == null) {
      throw new BadRequestException("The uploaded file does not contain any objects to import.");
    }

    if (probe.has("pledge_id") || probe.has("ship_code")) {
      List<ShiplistEntryDto> raw =
          objectMapper.convertValue(root, new TypeReference<List<ShiplistEntryDto>>() {});
      return raw.stream()
          .map(HangarImportService::mapShiplistEntry)
          .filter(Objects::nonNull)
          .toList();
    }
    if (probe.has("shipname") || probe.has("type")) {
      List<FleetviewEntryDto> raw =
          objectMapper.convertValue(root, new TypeReference<List<FleetviewEntryDto>>() {});
      return raw.stream()
          .map(HangarImportService::mapFleetviewEntry)
          .filter(Objects::nonNull)
          .toList();
    }
    throw new BadRequestException(
        "Unknown ship-list format. Expected CCU Game Fleetview or HangarXPLOR Shiplist JSON.");
  }

  /**
   * Lifts a Fleetview record into the internal representation. The shipname becomes the individual
   * name when non-blank; insurance is always left {@code null} because Fleetview carries no
   * insurance information.
   *
   * @param dto raw Fleetview entry
   * @return internal entry, or {@code null} if the model name is blank
   */
  private static @Nullable FleetImportEntry mapFleetviewEntry(@Nullable FleetviewEntryDto dto) {
    if (dto == null || dto.name() == null || dto.name().isBlank()) {
      return null;
    }
    String individual =
        (dto.shipname() != null && !dto.shipname().isBlank()) ? dto.shipname().trim() : null;
    return new FleetImportEntry(dto.name(), individual, null);
  }

  /**
   * Lifts a HangarXPLOR Shiplist record into the internal representation. Non-{@code "ship"} entity
   * types are dropped. The individual-name heuristic discards a {@code ship_name} that is just an
   * abbreviation/echo of {@code name} (e.g. {@code ship_name="325a"} for {@code name="325a
   * Fighter"}), so only truly custom names like {@code "KRT Olymp"} survive. {@code lti=true} maps
   * to insurance {@code "LTI"}; {@code lti=false} or absent leaves insurance {@code null} so the
   * caller falls back to {@link #DEFAULT_INSURANCE}.
   *
   * @param dto raw HangarXPLOR entry
   * @return internal entry, or {@code null} if the entity type or model name make it ineligible
   */
  private static @Nullable FleetImportEntry mapShiplistEntry(@Nullable ShiplistEntryDto dto) {
    if (dto == null || dto.name() == null || dto.name().isBlank()) {
      return null;
    }
    if (dto.entityType() != null && !"ship".equalsIgnoreCase(dto.entityType())) {
      return null;
    }
    String individual = computeIndividualNameFromShiplist(dto.name(), dto.shipName());
    String insurance = Boolean.TRUE.equals(dto.lti()) ? LTI_INSURANCE : null;
    return new FleetImportEntry(dto.name(), individual, insurance);
  }

  /**
   * Decides whether {@code shipName} is a genuine custom name worth preserving on the imported ship
   * or just an abbreviation/echo of the model name. The check uses the same alphanumeric
   * normalisation as the matcher: if the normalised ship name is a substring of the normalised
   * model name, treat it as an echo and return {@code null}. Empirically (n=64 in the real
   * shiplist) this keeps the three actual custom names ({@code "KRT Olymp"}, {@code "KRT Falcon"},
   * {@code "KRT Franklin"}) and discards 12 model-name echoes like {@code "325a"} → {@code "325a
   * Fighter"} or {@code "Caterpillar"} → {@code "Caterpillar Pirate Edition"}.
   *
   * @param modelName value of the Shiplist {@code name} field
   * @param shipName value of the Shiplist {@code ship_name} field (nullable)
   * @return the trimmed custom name, or {@code null} if it is an echo or blank
   */
  private static @Nullable String computeIndividualNameFromShiplist(
      @NotNull String modelName, @Nullable String shipName) {
    if (shipName == null || shipName.isBlank()) {
      return null;
    }
    String normModel = normalizeForMatching(modelName);
    String normShip = normalizeForMatching(shipName);
    if (normShip.isEmpty() || normModel.contains(normShip)) {
      return null;
    }
    return shipName.trim();
  }

  /**
   * Loads every {@code ShipType} once and pre-computes all four lookup forms in a single pass:
   * exact case-insensitive key, normalised key, and an alphanumeric token set used by the
   * token-subset stages. The hash maps use {@code putIfAbsent} so that if two ship types collapse
   * to the same key (very unlikely — the {@code name} column has a unique constraint and ship-type
   * names are distinct in UEX) the first one encountered wins deterministically.
   *
   * @return the populated lookup index
   */
  private @NotNull ShipTypeIndex buildShipTypeIndex() {
    ShipTypeIndex idx = new ShipTypeIndex();
    for (ShipType st : shipTypeRepository.findAll()) {
      String name = st.getName();
      if (name == null || name.isBlank()) {
        continue;
      }
      idx.byExactLower.putIfAbsent(name.trim().toLowerCase(Locale.ROOT), st);
      String normalized = normalizeForMatching(name);
      if (!normalized.isEmpty()) {
        idx.byNormalized.putIfAbsent(normalized, st);
      }
      Set<String> tokens = tokenize(name);
      if (!tokens.isEmpty()) {
        idx.tokenized.add(new TokenView(st, tokens));
      }
    }
    return idx;
  }

  /**
   * Four-stage resolution against the pre-built index. The earlier stages are deterministic
   * one-shot map lookups; the later (token-subset) stages scan the tokenised view and require the
   * candidate to be unique so an ambiguous abbreviation cannot silently map to the wrong variant.
   *
   * @param index lookup index produced by {@link #buildShipTypeIndex()}
   * @param rawName trimmed entry name from the upload
   * @return the matching {@code ShipType} or {@code null} if no stage produces a unique hit
   */
  private static @Nullable ShipType resolveShipType(
      @NotNull ShipTypeIndex index, @NotNull String rawName) {
    // Stage 1: exact case-insensitive
    ShipType match = index.byExactLower.get(rawName.toLowerCase(Locale.ROOT));
    if (match != null) {
      return match;
    }

    // Stage 2: normalised (lowercase + non-alphanumeric stripped)
    String normalized = normalizeForMatching(rawName);
    if (!normalized.isEmpty()) {
      match = index.byNormalized.get(normalized);
      if (match != null) {
        return match;
      }
    }

    // Stages 3 + 4: token-subset matches, both directions, each requiring uniqueness.
    Set<String> fvTokens = tokenize(rawName);
    if (fvTokens.isEmpty()) {
      return null;
    }

    // Stage 3: fv ⊆ uex — fv is an abbreviation of a uex name. Skip on ambiguity, do NOT fall
    // through to stage 4 — multiple uex candidates contain the fv tokens means the fv string is
    // genuinely ambiguous (e.g. "F7C-M Super Hornet" between Mk I / Heartseeker / Mk II).
    ShipType uniqueSubset = findUniqueWhereFvSubsetOfUex(index.tokenized, fvTokens);
    if (uniqueSubset != null) {
      return uniqueSubset;
    }
    if (anyFvSubsetOfUex(index.tokenized, fvTokens)) {
      return null;
    }

    // Stage 4: uex ⊆ fv — uex name is a shorter canonical form of the longer fv export name.
    return findUniqueWhereUexSubsetOfFv(index.tokenized, fvTokens);
  }

  /**
   * Returns the single {@code ShipType} whose token set contains the entire {@code fvTokens} set,
   * or {@code null} if zero or more than one candidate satisfies the predicate.
   *
   * @param tokenized tokenised view of every ship type
   * @param fvTokens token set parsed from the upload entry name
   * @return the unique {@code ShipType} or {@code null} (no match or ambiguous)
   */
  private static @Nullable ShipType findUniqueWhereFvSubsetOfUex(
      @NotNull List<TokenView> tokenized, @NotNull Set<String> fvTokens) {
    ShipType found = null;
    for (TokenView tv : tokenized) {
      if (tv.tokens.containsAll(fvTokens)) {
        if (found != null) {
          return null;
        }
        found = tv.shipType;
      }
    }
    return found;
  }

  /**
   * Cheap pre-check used to short-circuit Stage 4 when Stage 3 already saw multiple candidates.
   * Returns {@code true} as soon as any ship-type token set contains the fv token set — i.e. the
   * upload entry is a strict abbreviation of at least one canonical name.
   *
   * @param tokenized tokenised view of every ship type
   * @param fvTokens token set parsed from the upload entry name
   * @return {@code true} iff at least one candidate satisfies fv ⊆ uex
   */
  private static boolean anyFvSubsetOfUex(
      @NotNull List<TokenView> tokenized, @NotNull Set<String> fvTokens) {
    for (TokenView tv : tokenized) {
      if (tv.tokens.containsAll(fvTokens)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the single {@code ShipType} whose token set is contained in {@code fvTokens}, or {@code
   * null} if zero or more than one candidate satisfies the predicate. Reverse-direction counterpart
   * of {@link #findUniqueWhereFvSubsetOfUex(List, Set)} — handles upload names that are
   * <em>longer</em> than UEX's canonical short form (e.g. {@code "Ursa Rover"} → {@code "Ursa"}).
   *
   * @param tokenized tokenised view of every ship type
   * @param fvTokens token set parsed from the upload entry name
   * @return the unique {@code ShipType} or {@code null} (no match or ambiguous)
   */
  private static @Nullable ShipType findUniqueWhereUexSubsetOfFv(
      @NotNull List<TokenView> tokenized, @NotNull Set<String> fvTokens) {
    ShipType found = null;
    for (TokenView tv : tokenized) {
      if (!tv.tokens.isEmpty() && fvTokens.containsAll(tv.tokens)) {
        if (found != null) {
          return null;
        }
        found = tv.shipType;
      }
    }
    return found;
  }

  /**
   * Folds a ship name to a case-insensitive, punctuation-free comparison form. {@code "L-21 Wolf"}
   * and {@code "L21 Wolf"} both collapse to {@code "l21wolf"}; {@code "Cyclone-AA"} and {@code
   * "Cyclone AA"} both collapse to {@code "cycloneaa"}. ASCII-only by design — Star Citizen ship
   * names contain no diacritics, so we deliberately strip anything outside {@code [a-z0-9]}.
   *
   * @param name raw name (nullable)
   * @return the normalised form (never null; empty string for null/empty input)
   */
  private static @NotNull String normalizeForMatching(@Nullable String name) {
    if (name == null) {
      return "";
    }
    return NON_ALNUM.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("");
  }

  /**
   * Splits a ship name into an alphanumeric token set. Used by the token-subset match stages. Empty
   * tokens (from leading/trailing/multiple separators) are dropped.
   *
   * @param name raw name (nullable)
   * @return the token set (never null; empty set for null/empty input)
   */
  private static @NotNull Set<String> tokenize(@Nullable String name) {
    if (name == null || name.isBlank()) {
      return Set.of();
    }
    Set<String> tokens = new HashSet<>();
    for (String t : TOKEN_SPLIT.split(name.toLowerCase(Locale.ROOT))) {
      if (!t.isEmpty()) {
        tokens.add(t);
      }
    }
    return tokens;
  }

  /**
   * Format-agnostic representation of a single upload entry after Fleetview/Shiplist mapping. Used
   * internally so the resolver does not have to branch on the source format.
   *
   * @param name the ship-model name to resolve against {@code ShipType.name}
   * @param individualName custom display name for the ship, or {@code null}
   * @param insurance explicit insurance string (e.g. {@code "LTI"}), or {@code null} to let the
   *     service fall back to {@link #DEFAULT_INSURANCE}
   */
  private record FleetImportEntry(
      @NotNull String name, @Nullable String individualName, @Nullable String insurance) {}

  /**
   * Pre-computed multi-key view over the {@code ship_type} table. Built once per import via {@link
   * #buildShipTypeIndex()} so per-entry resolution touches only in-memory structures.
   */
  private static final class ShipTypeIndex {
    private final Map<String, ShipType> byExactLower = new HashMap<>();
    private final Map<String, ShipType> byNormalized = new HashMap<>();
    private final List<TokenView> tokenized = new ArrayList<>();
  }

  /**
   * Pairing of a {@code ShipType} with its tokenised alphanumeric form. Used by the Stage 3 / 4
   * token-subset comparisons. Records are not used here because the surrounding class is not a
   * record-friendly context and Lombok is overkill for a one-field-pair holder.
   */
  private static final class TokenView {
    private final ShipType shipType;
    private final Set<String> tokens;

    private TokenView(@NotNull ShipType shipType, @NotNull Set<String> tokens) {
      this.shipType = shipType;
      this.tokens = tokens;
    }
  }
}
