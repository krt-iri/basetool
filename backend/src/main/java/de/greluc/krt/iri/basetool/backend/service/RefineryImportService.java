/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.config.RefineryImportProperties;
import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.mapper.LocationMapper;
import de.greluc.krt.iri.basetool.backend.mapper.MaterialMapper;
import de.greluc.krt.iri.basetool.backend.mapper.RefiningMethodMapper;
import de.greluc.krt.iri.basetool.backend.mapper.UserMapper;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialExternalAliasSource;
import de.greluc.krt.iri.basetool.backend.model.MaterialType;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.RefiningMethod;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportSuggestionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.ImportIssueCode;
import de.greluc.krt.iri.basetool.backend.model.dto.ImportIssueDto;
import de.greluc.krt.iri.basetool.backend.model.dto.ImportIssueSeverity;
import de.greluc.krt.iri.basetool.backend.model.dto.ImportSuggestionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryExtractDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryExtractGoodDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryExtractOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryGoodDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryImportDraftDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefiningMethodRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import de.greluc.krt.iri.basetool.backend.service.BlueprintProductService.ResolvedProduct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Turns a {@code RefineryExtract} JSON (frozen contract, plan §5) into a non-persisted {@link
 * RefineryImportDraftDto} by matching the verbatim screen reads against master data — Phase 1 of
 * the refinery screenshot import (#434, epic #439).
 *
 * <p><b>Error semantics (plan §8):</b> envelope-level problems (unsupported {@code schemaVersion},
 * non-SETUP {@code panelType}) throw {@link BadRequestException} with an i18n key. Every
 * content-level problem (unmatched name, skipped row, checksum mismatch) yields a draft plus {@link
 * ImportIssueDto}s — never a 400, so the user always sees what was read.
 *
 * <p><b>Material matching (plan §7.3, deterministic, stop at first hit):</b> both sides are folded
 * with {@link MaterialNameCanonicalizer} (master data stores {@code "Stileron (Raw)"}, the screen
 * shows {@code "STILERON (ORE)"}); the candidate set mirrors the create path's input gate ({@code
 * type == RAW || isManualRawMaterial}, visible only). Stages: unique canonical match → curated
 * {@code REFINERY_SCREEN} alias → unique suffix/contains (game-UI-truncated names) → fuzzy via the
 * reused {@link BlueprintFuzzyMatcher} with a conservative accept threshold; fuzzy hits are never
 * silent ({@code LOW_CONFIDENCE_MATERIAL}), misses keep the row with ranked suggestions.
 *
 * <p>Matching never touches the security context (kept pure and unit-testable); the caller id is
 * passed in explicitly and used only to default the draft's owner to the uploading user (§7.4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefineryImportService {

  /** The only {@code RefineryExtract} schema version this backend accepts. */
  public static final int SUPPORTED_SCHEMA_VERSION = 1;

  /** The only panel type v1 drafts from; PROCESSING/UNKNOWN are envelope-level rejects. */
  private static final String PANEL_TYPE_SETUP = "SETUP";

  /**
   * Minimum canonical-core length for the suffix/contains stage. Guards against a heavily truncated
   * read (e.g. {@code "RE"}) accidentally being "contained" in half the catalogue.
   */
  private static final int MIN_PARTIAL_MATCH_LENGTH = 5;

  /** Savable quality range of the {@code refinery_good.quality} column (entity constraint). */
  private static final int QUALITY_MIN = 0;

  /** Upper bound of the savable quality range. */
  private static final int QUALITY_MAX = 1000;

  private final MaterialRepository materialRepository;
  private final RefiningMethodRepository refiningMethodRepository;
  private final LocationRepository locationRepository;
  private final UserRepository userRepository;
  private final MaterialExternalAliasService materialExternalAliasService;
  private final BlueprintFuzzyMatcher fuzzyMatcher;
  private final RefineryImportProperties properties;
  private final MaterialMapper materialMapper;
  private final LocationMapper locationMapper;
  private final RefiningMethodMapper refiningMethodMapper;
  private final UserMapper userMapper;

  /**
   * Validates the extract envelope and assembles the best-effort draft: resolves location and
   * method, walks the goods of {@code orders[0]} in on-screen order, matches materials per §7.3,
   * skips rows the create path could never accept (refine-off, zero quantity, un-quoted) with a
   * matching issue, reconciles the panel-header totals, and reports every finding as an {@link
   * ImportIssueDto}. Nothing is persisted.
   *
   * @param extract the validated {@code RefineryExtract} payload
   * @param callerId id of the uploading user; the draft's owner defaults to this user (§7.4) when
   *     the id resolves, and stays {@code null} otherwise
   * @return the draft order plus issues and match counters
   * @throws BadRequestException with an i18n key when {@code schemaVersion != 1} or {@code
   *     orders[0].panelType} is not {@code SETUP}
   */
  public RefineryImportDraftDto buildDraft(
      @NotNull RefineryExtractDto extract, @Nullable UUID callerId) {
    if (extract.schemaVersion() == null || extract.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
      throw new BadRequestException("error.refineryImport.unsupportedSchemaVersion");
    }
    RefineryExtractOrderDto order = extract.orders().getFirst();
    if (order.panelType() == null || !PANEL_TYPE_SETUP.equalsIgnoreCase(order.panelType().trim())) {
      throw new BadRequestException("error.refineryImport.unsupportedPanelType");
    }

    List<ImportIssueDto> issues = new ArrayList<>();
    if (extract.orders().size() > 1) {
      issues.add(
          issue(
              "orders",
              String.valueOf(extract.orders().size()),
              ImportIssueCode.MULTIPLE_ORDERS_TRUNCATED,
              ImportIssueSeverity.INFO,
              null,
              null));
    }

    Optional<Location> location = matchRefineryLocation(order.rawLocationName());
    if (location.isEmpty()) {
      issues.add(
          issue(
              "location",
              order.rawLocationName(),
              ImportIssueCode.UNRESOLVED_LOCATION,
              ImportIssueSeverity.WARNING,
              null,
              null));
    }
    Optional<RefiningMethod> method = matchMethod(order.rawMethodName());
    if (method.isEmpty()) {
      issues.add(
          issue(
              "refiningMethod",
              order.rawMethodName(),
              ImportIssueCode.UNRESOLVED_METHOD,
              ImportIssueSeverity.WARNING,
              null,
              null));
    }

    List<RefineryExtractGoodDto> sourceGoods = sortedByRowIndex(order.goods());
    boolean orderUnquoted =
        Boolean.FALSE.equals(order.quoted())
            || (!sourceGoods.isEmpty()
                && sourceGoods.stream().allMatch(g -> g.outputQuantity() == null));
    if (orderUnquoted) {
      issues.add(
          issue(
              "quoted",
              String.valueOf(order.quoted()),
              ImportIssueCode.UNQUOTED_ORDER,
              ImportIssueSeverity.BLOCKING,
              null,
              null));
    }

    MatchContext context = prepareMatchContext();
    List<RefineryGoodDto> draftGoods = new ArrayList<>();
    int goodsMatched = 0;
    int rowsSkipped = 0;
    for (int sourceIndex = 0; sourceIndex < sourceGoods.size(); sourceIndex++) {
      RefineryExtractGoodDto good = sourceGoods.get(sourceIndex);
      int screenRow = good.rowIndex() != null ? good.rowIndex() : sourceIndex;
      ImportIssueCode skipReason = skipReason(good);
      if (skipReason != null) {
        rowsSkipped++;
        issues.add(
            issue(
                "goods[" + screenRow + "]",
                good.rawMaterialName(),
                skipReason,
                skipReason == ImportIssueCode.SKIPPED_REFINE_OFF
                    ? ImportIssueSeverity.INFO
                    : ImportIssueSeverity.WARNING,
                good.confidence(),
                null));
        continue;
      }
      int draftIndex = draftGoods.size();
      MaterialMatch match = matchMaterialDetailed(good.rawMaterialName(), context);
      MaterialDto inputMaterial = null;
      MaterialDto outputMaterial = null;
      if (match.material() != null) {
        goodsMatched++;
        inputMaterial = materialMapper.toDto(match.material());
        if (match.fuzzy()) {
          issues.add(
              issue(
                  "goods[" + draftIndex + "].inputMaterial",
                  good.rawMaterialName(),
                  ImportIssueCode.LOW_CONFIDENCE_MATERIAL,
                  ImportIssueSeverity.WARNING,
                  match.score(),
                  match.suggestions()));
        }
        Material refined = match.material().getRefinedMaterial();
        if (refined != null) {
          outputMaterial = materialMapper.toDto(refined);
        } else {
          issues.add(
              issue(
                  "goods[" + draftIndex + "].outputMaterial",
                  match.material().getName(),
                  ImportIssueCode.NO_REFINED_MATERIAL,
                  ImportIssueSeverity.INFO,
                  null,
                  null));
        }
      } else {
        issues.add(
            issue(
                "goods[" + draftIndex + "].inputMaterial",
                good.rawMaterialName(),
                ImportIssueCode.UNMATCHED_MATERIAL,
                ImportIssueSeverity.WARNING,
                good.confidence(),
                match.suggestions()));
      }
      int quality = good.quality() != null ? good.quality() : 0;
      if (quality < QUALITY_MIN || quality > QUALITY_MAX) {
        issues.add(
            issue(
                "goods[" + draftIndex + "].quality",
                String.valueOf(good.quality()),
                ImportIssueCode.OUT_OF_RANGE_QUALITY,
                ImportIssueSeverity.WARNING,
                good.confidence(),
                null));
      }
      draftGoods.add(
          new RefineryGoodDto(
              null,
              inputMaterial,
              good.inputQuantity(),
              outputMaterial,
              good.outputQuantity(),
              quality,
              null));
    }

    reconcileHeaderTotals(order, sourceGoods, issues);

    RefineryOrderDto draftOrder =
        new RefineryOrderDto(
            null,
            resolveOwnerReference(callerId),
            location.map(locationMapper::toDto).orElse(null),
            null,
            null,
            order.durationMinutes(),
            order.expenses(),
            null,
            null,
            null,
            method.map(refiningMethodMapper::toDto).orElse(null),
            RefineryOrderStatus.OPEN.name(),
            draftGoods,
            null,
            null,
            null);
    log.debug(
        "Built refinery import draft: {} of {} rows matched, {} skipped, {} issues",
        goodsMatched,
        sourceGoods.size(),
        rowsSkipped,
        issues.size());
    return new RefineryImportDraftDto(
        draftOrder, List.copyOf(issues), goodsMatched, sourceGoods.size(), rowsSkipped);
  }

  /**
   * Resolves a raw screen material name against the refinery-input candidate set using the §7.3
   * stages. Convenience view of {@link #matchMaterialDetailed(String, MatchContext)} for callers
   * that only need the hit, not the score or suggestions.
   *
   * @param rawName verbatim screen read, e.g. {@code "STILERON (ORE)"}
   * @return the matched material, or empty when no stage produced a hit
   */
  public Optional<Material> matchMaterial(@Nullable String rawName) {
    return Optional.ofNullable(matchMaterialDetailed(rawName, prepareMatchContext()).material());
  }

  /**
   * Resolves a raw method read case-insensitively against {@code refining_method.name} — UEX stores
   * title case ({@code "Ferron Exchange"}), the screen renders uppercase.
   *
   * @param rawName verbatim screen read; null/blank yields empty
   * @return the matched refining method, or empty
   */
  public Optional<RefiningMethod> matchMethod(@Nullable String rawName) {
    if (!StringUtils.hasText(rawName)) {
      return Optional.empty();
    }
    return refiningMethodRepository.findByNameIgnoreCase(rawName.trim());
  }

  /**
   * Resolves a raw location read against the refinery-equipped locations (the create-form picker
   * source) by unique canonical name — e.g. screen {@code "LEVSKI"} to master {@code "Levski"}.
   *
   * @param rawName verbatim terminal-header read; null/blank yields empty (the normal case for
   *     pre-cropped panel input, which never contains the header)
   * @return the matched location, or empty when none or several candidates share the folded name
   */
  public Optional<Location> matchRefineryLocation(@Nullable String rawName) {
    String canonical = MaterialNameCanonicalizer.canonicalCore(rawName);
    if (canonical == null || canonical.isEmpty()) {
      return Optional.empty();
    }
    List<Location> hits =
        locationRepository.findLocationsWithRefinery().stream()
            .filter(l -> canonical.equals(MaterialNameCanonicalizer.canonicalCore(l.getName())))
            .toList();
    return hits.size() == 1 ? Optional.of(hits.getFirst()) : Optional.empty();
  }

  /**
   * Classifies a source row that must not become a draft good: REFINE toggle off, un-quoted YIELD
   * ({@code outputQuantity == null}), or a zero quantity the create path's {@code @Min(1)} would
   * reject anyway.
   *
   * @param good the source row
   * @return the skip reason, or {@code null} when the row is draftable
   */
  private @Nullable ImportIssueCode skipReason(RefineryExtractGoodDto good) {
    if (Boolean.FALSE.equals(good.refine())) {
      return ImportIssueCode.SKIPPED_REFINE_OFF;
    }
    if (good.outputQuantity() == null) {
      return ImportIssueCode.UNQUOTED_ROW;
    }
    if (good.inputQuantity() == null || good.inputQuantity() < 1 || good.outputQuantity() < 1) {
      return ImportIssueCode.SKIPPED_ZERO_QTY;
    }
    return null;
  }

  /**
   * Reconciles the panel-header totals against the row quantities and flags a mismatch as {@code
   * SUM_MISMATCH} (a scrolled screenshot may be missing from the capture set). v1 semantics
   * (hypothesis from the golden set, to be confirmed/frozen by Phase 0 #433): {@code IN MANIFEST} =
   * sum of <em>all</em> row quantities, {@code TO REFINE} = sum of refine-ON row quantities.
   *
   * @param order the extracted order carrying the nullable header totals
   * @param sourceGoods all source rows (including skipped ones)
   * @param issues sink for the mismatch findings
   */
  private void reconcileHeaderTotals(
      RefineryExtractOrderDto order,
      List<RefineryExtractGoodDto> sourceGoods,
      List<ImportIssueDto> issues) {
    if (order.rawInManifestTotal() != null) {
      long sumAll =
          sourceGoods.stream()
              .mapToLong(g -> g.inputQuantity() != null ? g.inputQuantity() : 0L)
              .sum();
      if (sumAll != order.rawInManifestTotal()) {
        issues.add(
            issue(
                "rawInManifestTotal",
                order.rawInManifestTotal() + " != " + sumAll,
                ImportIssueCode.SUM_MISMATCH,
                ImportIssueSeverity.WARNING,
                null,
                null));
      }
    }
    if (order.rawToRefineTotal() != null) {
      long sumRefineOn =
          sourceGoods.stream()
              .filter(g -> !Boolean.FALSE.equals(g.refine()))
              .mapToLong(g -> g.inputQuantity() != null ? g.inputQuantity() : 0L)
              .sum();
      if (sumRefineOn != order.rawToRefineTotal()) {
        issues.add(
            issue(
                "rawToRefineTotal",
                order.rawToRefineTotal() + " != " + sumRefineOn,
                ImportIssueCode.SUM_MISMATCH,
                ImportIssueSeverity.WARNING,
                null,
                null));
      }
    }
  }

  /**
   * Loads the candidate set once per request and pre-computes the canonical-core index plus the
   * name-keyed lookup the fuzzy stage maps its results back through.
   *
   * @return the per-request matching context
   */
  private MatchContext prepareMatchContext() {
    List<Material> candidates = materialRepository.findRefineryInputCandidates(MaterialType.RAW);
    Map<String, List<Material>> canonicalIndex = new HashMap<>();
    Map<String, Material> byName = new HashMap<>();
    for (Material candidate : candidates) {
      byName.put(candidate.getName(), candidate);
      String canonical = MaterialNameCanonicalizer.canonicalCore(candidate.getName());
      if (canonical != null && !canonical.isEmpty()) {
        canonicalIndex.computeIfAbsent(canonical, k -> new ArrayList<>()).add(candidate);
      }
    }
    return new MatchContext(candidates, canonicalIndex, byName);
  }

  /**
   * Runs the §7.3 stages against the prepared context: unique canonical match, curated {@code
   * REFINERY_SCREEN} alias, unique suffix/contains for game-UI-truncated reads, then the fuzzy
   * fallback. Fuzzy results at or above the accept threshold match (flagged); everything below
   * leaves the row unmatched but carries the ranked suggestions.
   *
   * @param rawName verbatim screen read
   * @param context the per-request candidate context
   * @return the detailed outcome (never {@code null}; an unmatchable name yields an empty result)
   */
  private MaterialMatch matchMaterialDetailed(@Nullable String rawName, MatchContext context) {
    String canonical = MaterialNameCanonicalizer.canonicalCore(rawName);
    if (canonical == null || canonical.isEmpty()) {
      return MaterialMatch.unmatched(null);
    }
    List<Material> exact = context.canonicalIndex().getOrDefault(canonical, List.of());
    if (exact.size() == 1) {
      return MaterialMatch.exact(exact.getFirst());
    }

    Material viaAlias =
        materialExternalAliasService.resolveMaterialByAlias(
            MaterialExternalAliasSource.REFINERY_SCREEN, rawName);
    if (viaAlias != null) {
      return MaterialMatch.exact(viaAlias);
    }

    if (canonical.length() >= MIN_PARTIAL_MATCH_LENGTH) {
      List<Material> partial =
          context.candidates().stream()
              .filter(
                  c -> {
                    String candidateCanonical =
                        MaterialNameCanonicalizer.canonicalCore(c.getName());
                    return candidateCanonical != null && candidateCanonical.contains(canonical);
                  })
              .toList();
      if (partial.size() == 1) {
        return MaterialMatch.exact(partial.getFirst());
      }
    }

    String fuzzyKey = MaterialNameCanonicalizer.fuzzyKey(rawName);
    if (fuzzyKey == null || fuzzyKey.isEmpty()) {
      return MaterialMatch.unmatched(null);
    }
    List<ResolvedProduct> wrapped =
        context.candidates().stream()
            .map(
                c ->
                    new ResolvedProduct(
                        MaterialNameCanonicalizer.fuzzyKey(c.getName()), c.getName(), null))
            .filter(p -> p.productKey() != null && !p.productKey().isEmpty())
            .toList();
    List<BlueprintImportSuggestionDto> ranked =
        fuzzyMatcher.topSuggestions(
            fuzzyKey, wrapped, properties.getSuggestionLimit(), properties.getSuggestionFloor());
    List<ImportSuggestionDto> suggestions =
        ranked.stream()
            .map(
                s -> {
                  Material candidate = context.byName().get(s.productName());
                  return candidate == null
                      ? null
                      : new ImportSuggestionDto(candidate.getId(), candidate.getName(), s.score());
                })
            .filter(s -> s != null)
            .toList();
    if (!ranked.isEmpty() && ranked.getFirst().score() >= properties.getFuzzyAcceptThreshold()) {
      Material best = context.byName().get(ranked.getFirst().productName());
      if (best != null) {
        return MaterialMatch.fuzzy(best, ranked.getFirst().score(), suggestions);
      }
    }
    return MaterialMatch.unmatched(suggestions.isEmpty() ? null : suggestions);
  }

  /**
   * Returns the source rows ordered by their stitched on-screen {@code rowIndex} (nulls last, in
   * arrival order) so the draft's goods mirror the in-game screen top-to-bottom.
   *
   * @param goods the contract's goods list (never {@code null} after bean validation)
   * @return a new sorted list
   */
  private List<RefineryExtractGoodDto> sortedByRowIndex(List<RefineryExtractGoodDto> goods) {
    return goods.stream()
        .sorted(
            Comparator.comparing(
                RefineryExtractGoodDto::rowIndex, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }

  /**
   * Defaults the draft owner to the uploading user (§7.4 "owner defaults to the uploading user"). A
   * caller id that does not resolve to a user row simply leaves the owner empty — the review form
   * falls back to its own current-user default.
   *
   * @param callerId id of the authenticated uploader; may be {@code null} in unit-test contexts
   * @return the owner reference, or {@code null}
   */
  private @Nullable UserReferenceDto resolveOwnerReference(@Nullable UUID callerId) {
    if (callerId == null) {
      return null;
    }
    return userRepository.findById(callerId).map(userMapper::toReferenceDto).orElse(null);
  }

  /**
   * Shorthand factory keeping the issue-creation call sites readable.
   *
   * @param field dotted field path (see {@link ImportIssueDto})
   * @param rawValue verbatim read or compact diagnostic
   * @param code machine-readable reason
   * @param severity visual grading
   * @param confidence contextual confidence, nullable
   * @param suggestions ranked candidates, nullable
   * @return the assembled issue
   */
  private static ImportIssueDto issue(
      String field,
      @Nullable String rawValue,
      ImportIssueCode code,
      ImportIssueSeverity severity,
      @Nullable Double confidence,
      @Nullable List<ImportSuggestionDto> suggestions) {
    return new ImportIssueDto(field, rawValue, code, severity, confidence, suggestions);
  }

  /**
   * Per-request matching context: the create-path-gated candidate set plus the derived indexes.
   *
   * @param candidates visible {@code RAW || isManualRawMaterial} materials
   * @param canonicalIndex canonical core → candidates sharing it
   * @param byName exact display name → candidate (names are unique)
   */
  private record MatchContext(
      List<Material> candidates,
      Map<String, List<Material>> canonicalIndex,
      Map<String, Material> byName) {}

  /**
   * Detailed outcome of one material-matching run.
   *
   * @param material the matched material, or {@code null}
   * @param fuzzy {@code true} when only the fuzzy stage produced the hit (must be flagged)
   * @param score the fuzzy score for fuzzy hits, else {@code null}
   * @param suggestions ranked candidates for the review pick list, or {@code null}
   */
  private record MaterialMatch(
      @Nullable Material material,
      boolean fuzzy,
      @Nullable Double score,
      @Nullable List<ImportSuggestionDto> suggestions) {

    /**
     * A deterministic (canonical / alias / suffix) hit.
     *
     * @param material the matched material
     * @return the match result
     */
    static MaterialMatch exact(Material material) {
      return new MaterialMatch(material, false, null, null);
    }

    /**
     * A fuzzy-stage hit at or above the accept threshold.
     *
     * @param material the matched material
     * @param score the blended similarity score
     * @param suggestions the ranked alternatives shown alongside the flag
     * @return the match result
     */
    static MaterialMatch fuzzy(
        Material material, double score, List<ImportSuggestionDto> suggestions) {
      return new MaterialMatch(material, true, score, suggestions);
    }

    /**
     * No stage matched.
     *
     * @param suggestions ranked candidates for the manual pick list, or {@code null}
     * @return the match result
     */
    static MaterialMatch unmatched(@Nullable List<ImportSuggestionDto> suggestions) {
      return new MaterialMatch(null, false, null, suggestions);
    }
  }
}
