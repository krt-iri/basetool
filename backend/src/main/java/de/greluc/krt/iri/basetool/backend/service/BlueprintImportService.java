package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.model.BlueprintExternalAlias;
import de.greluc.krt.iri.basetool.backend.model.BlueprintExternalAliasSource;
import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportApplyRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportPreviewDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportResolutionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportResultDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportStatus;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportSuggestionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.ScmdbBlueprintEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.ScmdbImportFileDto;
import de.greluc.krt.iri.basetool.backend.repository.BlueprintExternalAliasRepository;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.PersonalBlueprintRepository;
import de.greluc.krt.iri.basetool.backend.service.BlueprintProductService.ResolvedProduct;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
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
 *   <li>{@link #previewImport(String, MultipartFile)} parses an uploaded SCMDB log-watcher JSON,
 *       de-duplicates by product name, and resolves each name against the master product list
 *       through a fixed chain — normalized exact match, then a curated {@code
 *       blueprint_external_alias} (SCMDB) lookup, then dependency-free fuzzy suggestions — flagging
 *       names the caller already owns. Nothing is persisted.
 *   <li>{@link #applyImport(String, List)} takes the user's per-name resolutions, creates the
 *       missing {@code personal_blueprint} rows, and — for every manual pick (where the name did
 *       not already match by normalization) — learns a {@code blueprint_external_alias} so the next
 *       import auto-resolves it.
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
   * Parses an uploaded SCMDB export and previews how each unique blueprint name resolves against
   * the master product list for {@code ownerSub}. No rows are persisted.
   *
   * @param ownerSub Keycloak {@code sub} the import is being previewed for (owned-flag computation)
   * @param file the uploaded SCMDB log-watcher JSON
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
    Set<String> ownedKeys = new HashSet<>(ownedKeys(ownerSub, productByKey.keySet()));
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

      if (ownedKeys.contains(product.productKey())) {
        alreadyOwned++;
        continue;
      }
      personalBlueprintRepository.save(
          newOwned(ownerSub, product, resolution.acquiredAt(), resolution.note()));
      ownedKeys.add(product.productKey());
      added++;
    }

    log.info(
        "Blueprint import apply for ownerSub={}: added={} aliasesLearned={} skipped={}"
            + " alreadyOwned={}",
        ownerSub,
        added,
        aliasesLearned,
        skipped,
        alreadyOwned);
    return new BlueprintImportResultDto(added, aliasesLearned, skipped, alreadyOwned);
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
   * the documented {@code {"blueprints": [...]}} object or a bare array of blueprint records.
   * Duplicate product names collapse to one entry that keeps the earliest {@code ts} as the
   * acquisition suggestion; blank names are dropped.
   *
   * @param file the uploaded SCMDB JSON
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
      throw new BadRequestException("The uploaded file could not be parsed as valid SCMDB JSON.");
    }

    List<ScmdbBlueprintEntryDto> raw;
    if (root != null && root.isArray()) {
      raw = objectMapper.convertValue(root, new TypeReference<List<ScmdbBlueprintEntryDto>>() {});
    } else if (root != null && root.isObject()) {
      raw = objectMapper.convertValue(root, ScmdbImportFileDto.class).blueprints();
    } else {
      raw = null;
    }
    if (raw == null) {
      throw new BadRequestException(
          "The uploaded file must contain a 'blueprints' array of SCMDB entries.");
    }

    // Collapse duplicates by exact (trimmed) name, keeping the earliest timestamp.
    LinkedHashMap<String, Double> minTsByName = new LinkedHashMap<>();
    for (ScmdbBlueprintEntryDto entry : raw) {
      if (entry == null || entry.productName() == null || entry.productName().isBlank()) {
        continue;
      }
      String name = entry.productName().trim();
      Double ts = entry.ts();
      if (!minTsByName.containsKey(name)) {
        minTsByName.put(name, ts);
      } else {
        Double current = minTsByName.get(name);
        if (ts != null && (current == null || ts < current)) {
          minTsByName.put(name, ts);
        }
      }
    }

    List<ParsedEntry> entries = new ArrayList<>(minTsByName.size());
    for (Map.Entry<String, Double> e : minTsByName.entrySet()) {
      entries.add(new ParsedEntry(e.getKey(), toInstant(e.getValue())));
    }
    return entries;
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
    if (keys.isEmpty()) {
      return Set.of();
    }
    Set<String> owned = new HashSet<>();
    for (PersonalBlueprint pb :
        personalBlueprintRepository.findAllByOwnerSubAndProductKeyIn(
            ownerSub, new ArrayList<>(keys))) {
      owned.add(pb.getProductKey());
    }
    return owned;
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
   * A single de-duplicated SCMDB entry after parsing: the external product name plus the earliest
   * acquisition timestamp seen for it.
   *
   * @param externalName the SCMDB {@code productName} (trimmed)
   * @param suggestedAcquiredAt the earliest {@code ts} as an instant, or {@code null}
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
