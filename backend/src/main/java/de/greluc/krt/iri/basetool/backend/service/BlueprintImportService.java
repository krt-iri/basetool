package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.model.BlueprintExternalAlias;
import de.greluc.krt.iri.basetool.backend.model.BlueprintExternalAliasSource;
import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintExportEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintExportFileDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportApplyRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportPreviewDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportResolutionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportResultDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportStatus;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportSuggestionDto;
import de.greluc.krt.iri.basetool.backend.repository.BlueprintExternalAliasRepository;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.PersonalBlueprintRepository;
import de.greluc.krt.iri.basetool.backend.service.BlueprintProductService.ResolvedProduct;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SCMDB blueprint import engine (#327, Phase 4). Two steps:
 *
 * <ol>
 *   <li>{@link #previewImport(String, MultipartFile)} parses an uploaded blueprint export — the
 *       SCMDB log-watcher or the <a
 *       href="https://github.com/krt-iri/basetool-bp-extractor">Basetool Blueprint Extractor</a>,
 *       both of which carry a {@code blueprints} array of identically-named entries — de-duplicates
 *       by product name, and resolves each name against the master product list through a fixed
 *       chain — normalized exact match, then a curated {@code blueprint_external_alias} lookup,
 *       then dependency-free fuzzy suggestions — flagging names the caller already owns. Nothing is
 *       persisted.
 *   <li>{@link #applyImport(String, List)} takes the user's per-name resolutions, creates the
 *       missing {@code personal_blueprint} rows, and — for every manual pick (where the name did
 *       not already match by normalization) — learns a {@code blueprint_external_alias} so the next
 *       import auto-resolves it. Re-importing an already-owned blueprint never inserts a duplicate
 *       (also guarded by the {@code (owner_sub, product_key)} unique constraint); it only pulls the
 *       stored acquisition time earlier when the import carries an earlier timestamp.
 * </ol>
 *
 * <p>The engine is {@code ownerSub}-parameterised and never reads the security context, so the
 * Phase 7 admin surface can drive an import on behalf of a target user.
 *
 * <p>Apply is bulk-safe per the CLAUDE.md detach-clear rule: it issues no {@code @Modifying
 * clearAutomatically} query, so saving one new row never detaches the siblings created earlier in
 * the same transaction — there is no second {@code @Version} bump and thus no spurious 409.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BlueprintImportService {

  /** External catalogue every import row and learned alias belongs to. */
  private static final BlueprintExternalAliasSource SOURCE = BlueprintExternalAliasSource.SCMDB;

  private final ObjectMapper objectMapper;
  private final BlueprintProductService blueprintProductService;
  private final BlueprintNameNormalizer normalizer;
  private final BlueprintFuzzyMatcher fuzzyMatcher;
  private final BlueprintExternalAliasRepository aliasRepository;
  private final PersonalBlueprintRepository personalBlueprintRepository;
  private final GameItemRepository gameItemRepository;

  /**
   * Parses an uploaded blueprint export (SCMDB log-watcher or Basetool Blueprint Extractor) and
   * previews how each unique blueprint name resolves against the master product list for {@code
   * ownerSub}. No rows are persisted.
   *
   * @param ownerSub Keycloak {@code sub} the import is being previewed for (owned-flag computation)
   * @param file the uploaded blueprint export JSON
   * @return the preview with per-name rows and per-status counts
   * @throws BadRequestException if the file is empty, not valid JSON, or carries no blueprint array
   */
  @NotNull
  public BlueprintImportPreviewDto previewImport(
      @NotNull String ownerSub, @NotNull MultipartFile file) {
    List<ParsedEntry> parsed = parse(file);

    Map<String, ResolvedProduct> productByKey = productIndex();
    List<ResolvedProduct> allProducts = new ArrayList<>(productByKey.values());

    // First pass: resolve each name (without the owned check) and collect resolved keys.
    List<Resolution> resolutions = new ArrayList<>(parsed.size());
    Set<String> resolvedKeys = new HashSet<>();
    for (ParsedEntry entry : parsed) {
      Resolution resolution = resolve(entry, productByKey, allProducts);
      resolutions.add(resolution);
      if (resolution.product != null) {
        resolvedKeys.add(resolution.product.productKey());
      }
    }

    // Second pass: a single bulk lookup decides which resolved products are already owned.
    Set<String> ownedKeys = ownedKeys(ownerSub, resolvedKeys);

    List<BlueprintImportEntryDto> entries = new ArrayList<>(resolutions.size());
    int matched = 0;
    int matchedByAlias = 0;
    int suggested = 0;
    int unmatched = 0;
    int alreadyOwned = 0;
    for (Resolution resolution : resolutions) {
      BlueprintImportStatus status = resolution.status;
      ResolvedProduct product = resolution.product;
      if (product != null && ownedKeys.contains(product.productKey())) {
        status = BlueprintImportStatus.ALREADY_OWNED;
      }
      switch (status) {
        case MATCHED -> matched++;
        case MATCHED_BY_ALIAS -> matchedByAlias++;
        case SUGGESTED -> suggested++;
        case UNMATCHED -> unmatched++;
        case ALREADY_OWNED -> alreadyOwned++;
        default -> {
          /* exhaustive */
        }
      }
      entries.add(
          new BlueprintImportEntryDto(
              resolution.externalName,
              status,
              product == null ? null : product.productKey(),
              product == null ? null : product.productName(),
              product == null ? null : product.outputItemId(),
              resolution.suggestedAcquiredAt,
              resolution.suggestions));
    }

    log.info(
        "Blueprint import preview for ownerSub={}: total={} matched={} alias={} suggested={}"
            + " unmatched={} alreadyOwned={}",
        ownerSub,
        entries.size(),
        matched,
        matchedByAlias,
        suggested,
        unmatched,
        alreadyOwned);
    return new BlueprintImportPreviewDto(
        entries.size(), matched, matchedByAlias, suggested, unmatched, alreadyOwned, entries);
  }

  /**
   * Applies the user's per-name resolutions: creates the missing owned-blueprint rows and learns an
   * alias for every manual pick. Blank choices and product keys that no longer resolve are skipped.
   * Repeated names / products within one request are de-duplicated, so re-submitting a preview is
   * idempotent.
   *
   * <p>An already-owned blueprint is never duplicated (also guarded by the {@code (owner_sub,
   * product_key)} unique constraint); re-importing it only pulls the stored acquisition time
   * earlier when the current import carries an earlier timestamp (a missing or later timestamp
   * leaves it untouched). That earlier value is written by mutating the managed entity and relying
   * on dirty-checking — no {@code save()} / {@code flush()} — per the CLAUDE.md concurrency rules.
   *
   * @param ownerSub Keycloak {@code sub} the rows are created for
   * @param resolutions the per-name decisions (see {@link BlueprintImportApplyRequest})
   * @return a summary of added / learned / skipped / already-owned counts
   */
  @Transactional
  @NotNull
  public BlueprintImportResultDto applyImport(
      @NotNull String ownerSub, @NotNull List<BlueprintImportResolutionDto> resolutions) {
    Map<String, ResolvedProduct> productByKey = productIndex();

    int added = 0;
    int aliasesLearned = 0;
    int skipped = 0;
    int alreadyOwned = 0;
    int acquiredAtUpdated = 0;
    Map<String, PersonalBlueprint> ownedByKey = ownedByKey(ownerSub, productByKey.keySet());
    Set<String> aliasNamesSeen = new HashSet<>();

    for (BlueprintImportResolutionDto resolution : resolutions) {
      String externalName =
          resolution.externalName() == null ? "" : resolution.externalName().trim();
      String chosenKey = resolution.productKey() == null ? "" : resolution.productKey().trim();
      if (chosenKey.isEmpty()) {
        skipped++;
        continue;
      }
      ResolvedProduct product = productByKey.get(chosenKey);
      if (product == null) {
        log.debug("Import apply: skipping unresolvable product key '{}'", chosenKey);
        skipped++;
        continue;
      }

      if (learnAliasIfManual(ownerSub, externalName, product, aliasNamesSeen)) {
        aliasesLearned++;
      }

      PersonalBlueprint existing = ownedByKey.get(product.productKey());
      if (existing != null) {
        // Re-import of an already-owned blueprint: never insert a duplicate (also guarded by the
        // (owner_sub, product_key) unique constraint); only pull the acquisition time earlier when
        // this import carries an earlier timestamp. Mutating the managed entity relies on
        // dirty-checking — no save()/flush() — per the CLAUDE.md concurrency rules.
        if (isEarlierAcquiredAt(resolution.acquiredAt(), existing.getAcquiredAt())) {
          existing.setAcquiredAt(resolution.acquiredAt());
          acquiredAtUpdated++;
        }
        alreadyOwned++;
        continue;
      }
      ownedByKey.put(
          product.productKey(),
          personalBlueprintRepository.save(
              newOwned(ownerSub, product, resolution.acquiredAt(), resolution.note())));
      added++;
    }

    log.info(
        "Blueprint import apply for ownerSub={}: added={} aliasesLearned={} skipped={}"
            + " alreadyOwned={} acquiredAtUpdated={}",
        ownerSub,
        added,
        aliasesLearned,
        skipped,
        alreadyOwned,
        acquiredAtUpdated);
    return new BlueprintImportResultDto(
        added, aliasesLearned, skipped, alreadyOwned, acquiredAtUpdated);
  }

  /**
   * Persists a {@code blueprint_external_alias} for a manual resolution — one where the external
   * name does not already normalize to the chosen product key — unless an alias for that name
   * already exists or was just created in this request.
   *
   * @param ownerSub Keycloak {@code sub} stamped as the alias creator
   * @param externalName the SCMDB name being resolved (exact, trimmed)
   * @param product the chosen product
   * @param aliasNamesSeen exact external names already aliased in this request (mutated)
   * @return {@code true} if a new alias row was persisted
   */
  private boolean learnAliasIfManual(
      @NotNull String ownerSub,
      @NotNull String externalName,
      @NotNull ResolvedProduct product,
      @NotNull Set<String> aliasNamesSeen) {
    if (externalName.isEmpty() || normalizer.normalize(externalName).equals(product.productKey())) {
      return false;
    }
    if (!aliasNamesSeen.add(externalName)
        || aliasRepository.findBySourceSystemAndExternalName(SOURCE, externalName).isPresent()) {
      return false;
    }
    BlueprintExternalAlias alias = new BlueprintExternalAlias();
    alias.setSourceSystem(SOURCE);
    alias.setExternalName(externalName);
    alias.setProductKey(product.productKey());
    alias.setProductName(product.productName());
    if (product.outputItemId() != null) {
      alias.setOutputItem(gameItemRepository.getReferenceById(product.outputItemId()));
    }
    alias.setCreatedBy(ownerSub);
    aliasRepository.save(alias);
    log.info(
        "Learned blueprint alias: external='{}' -> productKey='{}' by={}",
        externalName,
        product.productKey(),
        ownerSub);
    return true;
  }

  /**
   * Resolves one parsed name through the fixed chain (exact → alias → fuzzy), without the
   * owned-flag check which the caller applies afterwards from a single bulk lookup.
   *
   * @param entry the parsed external name + acquisition suggestion
   * @param productByKey master products indexed by normalized key
   * @param allProducts master products as a list (fuzzy candidate set)
   * @return the resolution (status, resolved product, suggestions)
   */
  @NotNull
  private Resolution resolve(
      @NotNull ParsedEntry entry,
      @NotNull Map<String, ResolvedProduct> productByKey,
      @NotNull List<ResolvedProduct> allProducts) {
    String normalized = normalizer.normalize(entry.externalName());

    ResolvedProduct exact = productByKey.get(normalized);
    if (exact != null) {
      return new Resolution(
          entry.externalName(),
          BlueprintImportStatus.MATCHED,
          exact,
          entry.suggestedAcquiredAt(),
          List.of());
    }

    ResolvedProduct viaAlias = resolveViaAlias(entry.externalName(), productByKey);
    if (viaAlias != null) {
      return new Resolution(
          entry.externalName(),
          BlueprintImportStatus.MATCHED_BY_ALIAS,
          viaAlias,
          entry.suggestedAcquiredAt(),
          List.of());
    }

    List<BlueprintImportSuggestionDto> suggestions =
        fuzzyMatcher.topSuggestions(
            normalized,
            allProducts,
            BlueprintFuzzyMatcher.DEFAULT_LIMIT,
            BlueprintFuzzyMatcher.DEFAULT_THRESHOLD);
    return new Resolution(
        entry.externalName(),
        suggestions.isEmpty() ? BlueprintImportStatus.UNMATCHED : BlueprintImportStatus.SUGGESTED,
        null,
        entry.suggestedAcquiredAt(),
        suggestions);
  }

  /**
   * Looks up a curated SCMDB alias for the raw external name and dereferences it to a master
   * product. Falls back to the alias's own name / output-item snapshot if the master no longer
   * carries that product key (a renamed-away product), so a learned alias never silently regresses
   * to unmatched.
   *
   * @param externalName the raw external name from the upload
   * @param productByKey master products indexed by normalized key
   * @return the resolved product, or {@code null} if no alias exists
   */
  @Nullable
  private ResolvedProduct resolveViaAlias(
      @NotNull String externalName, @NotNull Map<String, ResolvedProduct> productByKey) {
    Optional<BlueprintExternalAlias> alias =
        aliasRepository.findBySourceSystemAndExternalNameIgnoreCase(SOURCE, externalName);
    if (alias.isEmpty()) {
      return null;
    }
    BlueprintExternalAlias a = alias.get();
    ResolvedProduct fromMaster = productByKey.get(a.getProductKey());
    if (fromMaster != null) {
      return fromMaster;
    }
    return new ResolvedProduct(
        a.getProductKey(),
        a.getProductName(),
        a.getOutputItem() == null ? null : a.getOutputItem().getId());
  }

  /**
   * Reads the multipart body and converts it into the de-duplicated parsed entries. Accepts either
   * the documented {@code {"blueprints": [...]}} object (both the SCMDB log-watcher and the
   * Basetool Blueprint Extractor wrap their records this way) or a bare array of blueprint records.
   * Duplicate product names collapse to one entry that keeps the earliest acquisition time as the
   * suggestion; blank names are dropped.
   *
   * @param file the uploaded blueprint export JSON
   * @return parsed entries in first-seen order (possibly empty)
   * @throws BadRequestException if the file is empty, not valid JSON, or carries no blueprint array
   */
  @NotNull
  private List<ParsedEntry> parse(@NotNull MultipartFile file) {
    if (file.isEmpty()) {
      throw new BadRequestException("The uploaded file is empty.");
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(file.getInputStream());
    } catch (IOException | JacksonException e) {
      log.warn("Blueprint import: failed to parse JSON — {}", e.getMessage());
      throw new BadRequestException(
          "The uploaded file could not be parsed as valid blueprint export JSON.");
    }

    List<BlueprintExportEntryDto> raw;
    if (root != null && root.isArray()) {
      raw = objectMapper.convertValue(root, new TypeReference<List<BlueprintExportEntryDto>>() {});
    } else if (root != null && root.isObject()) {
      raw = objectMapper.convertValue(root, BlueprintExportFileDto.class).blueprints();
    } else {
      raw = null;
    }
    if (raw == null) {
      throw new BadRequestException(
          "The uploaded file must contain a 'blueprints' array (SCMDB log-watcher or Basetool"
              + " Blueprint Extractor export).");
    }

    // Collapse duplicates by exact (trimmed) name, keeping the earliest acquisition time.
    LinkedHashMap<String, Instant> earliestByName = new LinkedHashMap<>();
    for (BlueprintExportEntryDto entry : raw) {
      if (entry == null || entry.productName() == null || entry.productName().isBlank()) {
        continue;
      }
      String name = entry.productName().trim();
      Instant acquiredAt = acquiredAtOf(entry);
      if (!earliestByName.containsKey(name)) {
        earliestByName.put(name, acquiredAt);
      } else {
        Instant current = earliestByName.get(name);
        if (acquiredAt != null && (current == null || acquiredAt.isBefore(current))) {
          earliestByName.put(name, acquiredAt);
        }
      }
    }

    List<ParsedEntry> entries = new ArrayList<>(earliestByName.size());
    for (Map.Entry<String, Instant> e : earliestByName.entrySet()) {
      entries.add(new ParsedEntry(e.getKey(), e.getValue()));
    }
    return entries;
  }

  /**
   * Resolves a parsed entry's acquisition instant from whichever timestamp its source exporter
   * stamped: SCMDB's {@code ts} (fractional Unix epoch seconds) takes precedence, then the Basetool
   * Blueprint Extractor's {@code receivedAt} (ISO-8601 instant). A malformed {@code receivedAt} is
   * treated as absent rather than failing the whole import.
   *
   * @param entry the parsed export entry
   * @return the acquisition instant, or {@code null} if neither field is present and parseable
   */
  @Nullable
  private Instant acquiredAtOf(@NotNull BlueprintExportEntryDto entry) {
    if (entry.ts() != null) {
      return toInstant(entry.ts());
    }
    return parseInstant(entry.receivedAt());
  }

  /**
   * Parses an ISO-8601 instant string (e.g. {@code 2026-03-26T16:49:31.050Z}) leniently. A blank or
   * unparseable value yields {@code null} so one malformed record never aborts the import.
   *
   * @param iso the ISO-8601 instant string, or {@code null}
   * @return the parsed instant, or {@code null}
   */
  @Nullable
  private Instant parseInstant(@Nullable String iso) {
    if (iso == null || iso.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(iso.trim());
    } catch (DateTimeParseException e) {
      log.debug("Blueprint import: ignoring unparseable receivedAt '{}'", iso);
      return null;
    }
  }

  /**
   * Converts a fractional Unix-epoch-seconds timestamp into an {@link Instant} (millisecond
   * precision). {@code null} in yields {@code null} out.
   *
   * @param epochSeconds fractional epoch seconds (e.g. {@code 1774534484.296}), or {@code null}
   * @return the corresponding instant, or {@code null}
   */
  @Nullable
  private Instant toInstant(@Nullable Double epochSeconds) {
    return epochSeconds == null ? null : Instant.ofEpochMilli(Math.round(epochSeconds * 1000.0));
  }

  /**
   * Builds the master product index keyed by normalized product key, in master-scan order.
   *
   * @return the product index
   */
  @NotNull
  private Map<String, ResolvedProduct> productIndex() {
    Map<String, ResolvedProduct> map = new LinkedHashMap<>();
    for (ResolvedProduct product : blueprintProductService.allProducts()) {
      map.putIfAbsent(product.productKey(), product);
    }
    return map;
  }

  /**
   * Returns the subset of {@code keys} the owner already owns via a single bulk lookup.
   *
   * @param ownerSub Keycloak {@code sub} of the owner
   * @param keys the product keys to test
   * @return the owned product keys (empty if {@code keys} is empty)
   */
  @NotNull
  private Set<String> ownedKeys(@NotNull String ownerSub, @NotNull Set<String> keys) {
    return new HashSet<>(ownedByKey(ownerSub, keys).keySet());
  }

  /**
   * Loads the owner's existing blueprint rows for the given product keys, indexed by product key,
   * via a single bulk lookup. Backs {@link #applyImport}'s duplicate skip and earliest-acquisition
   * refresh; the returned entities are managed, so mutating one (e.g. its {@code acquiredAt}) is
   * flushed by dirty-checking without an explicit {@code save()}.
   *
   * @param ownerSub Keycloak {@code sub} of the owner
   * @param keys the product keys to load
   * @return owned rows indexed by product key (empty if {@code keys} is empty)
   */
  @NotNull
  private Map<String, PersonalBlueprint> ownedByKey(
      @NotNull String ownerSub, @NotNull Set<String> keys) {
    if (keys.isEmpty()) {
      return new HashMap<>();
    }
    Map<String, PersonalBlueprint> owned = new HashMap<>();
    for (PersonalBlueprint pb :
        personalBlueprintRepository.findAllByOwnerSubAndProductKeyIn(
            ownerSub, new ArrayList<>(keys))) {
      owned.putIfAbsent(pb.getProductKey(), pb);
    }
    return owned;
  }

  /**
   * Decides whether an owned row's acquisition time should be pulled to an incoming import value:
   * only when the incoming value is present and is either earlier than the stored one or fills a
   * stored {@code null}. A {@code null} incoming never overwrites a stored value, so re-importing
   * an export that lacks a timestamp can never erase a known acquisition time.
   *
   * @param incoming the acquisition instant from the current import, or {@code null}
   * @param existing the instant currently stored on the owned row, or {@code null}
   * @return {@code true} if {@code existing} should be replaced with {@code incoming}
   */
  private boolean isEarlierAcquiredAt(@Nullable Instant incoming, @Nullable Instant existing) {
    if (incoming == null) {
      return false;
    }
    return existing == null || incoming.isBefore(existing);
  }

  /**
   * Builds a new, unsaved owned-blueprint entity stamped with the resolved product, attaching the
   * output item as a lazy reference when the product carries one.
   *
   * @param ownerSub Keycloak {@code sub} of the owner
   * @param product the chosen product to stamp
   * @param acquiredAt optional acquisition time
   * @param note optional note
   * @return the new transient entity
   */
  @NotNull
  private PersonalBlueprint newOwned(
      @NotNull String ownerSub,
      @NotNull ResolvedProduct product,
      @Nullable Instant acquiredAt,
      @Nullable String note) {
    PersonalBlueprint entity = new PersonalBlueprint();
    entity.setOwnerSub(ownerSub);
    entity.setProductKey(product.productKey());
    entity.setProductName(product.productName());
    if (product.outputItemId() != null) {
      entity.setOutputItem(gameItemRepository.getReferenceById(product.outputItemId()));
    }
    entity.setAcquiredAt(acquiredAt);
    entity.setNote(note);
    return entity;
  }

  /**
   * A single de-duplicated export entry after parsing: the external product name plus the earliest
   * acquisition instant seen for it.
   *
   * @param externalName the export {@code productName} (trimmed)
   * @param suggestedAcquiredAt the earliest acquisition instant (from {@code ts} or {@code
   *     receivedAt}), or {@code null}
   */
  private record ParsedEntry(@NotNull String externalName, @Nullable Instant suggestedAcquiredAt) {}

  /**
   * Intermediate per-name resolution carried between the two preview passes. Mutable status is not
   * needed — the owned-flag override is applied when building the response DTO.
   *
   * @param externalName the SCMDB name
   * @param status the chain outcome before the owned-flag override
   * @param product the resolved product, or {@code null}
   * @param suggestedAcquiredAt acquisition suggestion from {@code ts}
   * @param suggestions fuzzy candidates (empty unless {@code status} is SUGGESTED)
   */
  private record Resolution(
      @NotNull String externalName,
      @NotNull BlueprintImportStatus status,
      @Nullable ResolvedProduct product,
      @Nullable Instant suggestedAcquiredAt,
      @NotNull List<BlueprintImportSuggestionDto> suggestions) {}
}
