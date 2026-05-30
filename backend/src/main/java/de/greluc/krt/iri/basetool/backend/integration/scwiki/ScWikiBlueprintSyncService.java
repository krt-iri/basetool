package de.greluc.krt.iri.basetool.backend.integration.scwiki;

import de.greluc.krt.iri.basetool.backend.config.ScWikiProperties;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiBlueprintDto;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiBlueprintIngredientDto;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiBlueprintModifierDto;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiBlueprintModifierSegmentDto;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiBlueprintRequirementChildDto;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiBlueprintRequirementGroupDto;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiBlueprintSummaryPropertyDto;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiResponseDto;
import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialExternalAliasSource;
import de.greluc.krt.iri.basetool.backend.model.SyncEventType;
import de.greluc.krt.iri.basetool.backend.model.SyncSourceSystem;
import de.greluc.krt.iri.basetool.backend.model.scwiki.Blueprint;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintDismantleReturn;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintIngredient;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintIngredientKind;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintModifierSegment;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintRequirementGroup;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintRequirementModifier;
import de.greluc.krt.iri.basetool.backend.model.scwiki.BlueprintSummaryProperty;
import de.greluc.krt.iri.basetool.backend.repository.BlueprintRepository;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.service.MaterialExternalAliasService;
import de.greluc.krt.iri.basetool.backend.service.SyncReportService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * R4 SC Wiki blueprint sync (SC_WIKI_SYNC_PLAN.md §8.2). Pulls {@code /api/blueprints} and upserts
 * the recipe graph into {@code blueprint} + {@code blueprint_ingredient} + {@code
 * blueprint_dismantle_return}, plus the {@code blueprint_requirement_group} / {@code
 * blueprint_requirement_modifier} / {@code blueprint_summary_property} graph that carries the
 * per-slot crafting stat contributions.
 *
 * <p><b>Why a per-blueprint detail fetch.</b> The list endpoint returns only the flat {@code
 * ingredients[]} (name / kind / quantity). The {@code requirement_groups[]} block — the named build
 * slots and their stat {@code modifiers[]} (e.g. {@code weapon_damage} ×0.95..×1.05) — and the
 * {@code summary_properties[]} roll-up are returned <em>only</em> by the detail endpoint {@code GET
 * /api/blueprints/{uuid}} (verified against the live API + OpenAPI). So this sync walks the list to
 * enumerate the ~1559 blueprint UUIDs, then fetches each blueprint's detail (paced via {@link
 * ScWikiClient#paceForRateLimit()}) to capture the stats — the same per-UUID closure pattern as
 * {@link ScWikiItemSyncService}. The persistence context is flushed + cleared every {@link
 * #FLUSH_BATCH_SIZE} blueprints so the run never accumulates the whole graph in one context.
 *
 * <p>Ingredient resolution: a {@code RESOURCE} line resolves to a {@code material} via {@code
 * scwiki_uuid} → alias table → exact name; an {@code ITEM} line resolves to a {@code game_item} via
 * {@code external_uuid}. The raw Wiki UUID + name snapshot are persisted on every line regardless,
 * so an unresolved line (FK {@code null}, {@link SyncEventType#UNRESOLVED_INGREDIENT} logged)
 * re-resolves on a later run once an alias is added or the item lands in {@code game_item} —
 * without re-fetching the Wiki.
 *
 * <p>When the detail carries {@code requirement_groups}, the owned ingredient / group / summary
 * collections are rebuilt in place (cleared, then re-added) and each ingredient is linked to its
 * slot; orphan removal deletes the previous generation on flush. When the detail is unavailable
 * (transient 404 / error), the sync falls back to the list's flat {@code ingredients[]} via the
 * legacy reuse-by-index path and <b>leaves any previously captured group / summary data
 * untouched</b> so a transient miss never wipes good stat data. No {@code @Modifying} bulk update
 * runs inside the per-blueprint loop, so the CLAUDE.md detach-clear trap does not apply.
 *
 * <p>Gated behind {@code krt.scwiki.blueprint-sync-enabled} (default {@code false}); ships dark
 * until an operator opts in per the runbook §4. Empty Wiki responses short-circuit before the
 * orphan sweep (§8.7).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScWikiBlueprintSyncService {

  /**
   * Flush/clear the persistence context every this-many blueprints so a full run (~1559 recipes,
   * each with its group / modifier / ingredient graph) never accumulates in one context — mirrors
   * the {@link ScWikiItemSyncService} M2 hardening.
   */
  static final int FLUSH_BATCH_SIZE = 500;

  private final ScWikiClient scWikiClient;
  private final ScWikiProperties properties;
  private final BlueprintRepository blueprintRepository;
  private final MaterialRepository materialRepository;
  private final MaterialExternalAliasService aliasService;
  private final GameItemRepository gameItemRepository;
  private final SyncReportService syncReportService;
  private final EntityManager entityManager;

  /**
   * Runs the full blueprint sync. No-op (with an INFO line) when the feature flag is off. An empty
   * Wiki list response short-circuits before the orphan sweep so a transient outage never wipes the
   * recipe graph. Each blueprint's detail (carrying {@code requirement_groups}) is fetched per UUID
   * and the persistence context is flushed + cleared every {@link #FLUSH_BATCH_SIZE} rows.
   */
  @Transactional
  public void syncBlueprints() {
    if (!Boolean.TRUE.equals(properties.getBlueprintSyncEnabled())) {
      log.info(
          "SC Wiki blueprint sync invoked but disabled "
              + "(krt.scwiki.blueprint-sync-enabled=false) — skipping.");
      return;
    }

    log.info("Starting SC Wiki blueprint sync...");
    List<ScWikiBlueprintDto> fetched =
        scWikiClient.fetchAllPages(
            properties.getBlueprintsEndpoint(),
            new ParameterizedTypeReference<ScWikiResponseDto<ScWikiBlueprintDto>>() {},
            "blueprints");
    if (fetched.isEmpty()) {
      log.warn("No blueprints received from SC Wiki API. Aborting sync (no orphan sweep).");
      return;
    }

    UUID runId = syncReportService.beginRun();
    Instant now = Instant.now();
    Set<UUID> seen = new HashSet<>();
    int processed = 0;
    int unresolvedLines = 0;
    int detailMisses = 0;

    for (ScWikiBlueprintDto listDto : fetched) {
      if (listDto.uuid() == null) {
        continue;
      }
      try {
        seen.add(listDto.uuid());
        // requirement_groups (the stat modifiers) and summary_properties are detail-only, so fetch
        // each blueprint's detail. Pace between calls exactly like the closure item sync.
        scWikiClient.paceForRateLimit();
        ScWikiBlueprintDto detail =
            scWikiClient.fetchOne(
                properties.getBlueprintsEndpoint() + "/" + listDto.uuid(),
                ScWikiBlueprintDto.class,
                "blueprint");
        if (detail == null) {
          detailMisses++;
        }
        ScWikiBlueprintDto dto = detail != null ? detail : listDto;

        Blueprint bp =
            blueprintRepository.findByScwikiUuid(listDto.uuid()).orElseGet(Blueprint::new);
        bp.setScwikiUuid(listDto.uuid());
        bp.setScwikiKey(dto.key());
        bp.setOutputName(dto.outputName());
        bp.setCategoryUuid(dto.categoryUuid());
        bp.setCraftTimeSeconds(dto.craftTimeSeconds());
        bp.setIsAvailableByDefault(Boolean.TRUE.equals(dto.isAvailableByDefault()));
        bp.setIngredientCount(dto.ingredientCount());
        bp.setUnlockingMissionsCount(dto.unlockingMissionsCount());
        bp.setGameVersionSeen(dto.gameVersion());
        bp.setOutputItem(resolveGameItem(dto.outputItemUuid()));
        applyDismantle(bp, dto);

        if (dto.requirementGroups() != null && !dto.requirementGroups().isEmpty()) {
          unresolvedLines += applyRequirementGraph(bp, dto, runId);
        } else {
          // Fallback: no detail (transient miss) — use the flat list ingredients and leave any
          // previously captured group / summary data untouched so a miss never wipes good stats.
          unresolvedLines += applyIngredients(bp, dto, runId);
        }
        applyDismantleReturns(bp, dto, runId);
        bp.setScwikiSyncedAt(now);
        bp.setScwikiDeletedAt(null);
        blueprintRepository.save(bp);
        processed++;
        if (processed % FLUSH_BATCH_SIZE == 0) {
          entityManager.flush();
          entityManager.clear();
        }
      } catch (Exception e) {
        log.error("Failed to process SC Wiki blueprint dto: {}", listDto, e);
      }
    }

    if (!seen.isEmpty()) {
      int marked = blueprintRepository.markScwikiDeleted(seen, now);
      if (marked > 0) {
        log.info("Marked {} blueprint row(s) scwiki_deleted (no longer in Wiki feed)", marked);
      }
    }
    syncReportService.pruneRuns(SyncSourceSystem.SCWIKI);
    log.info(
        "Finished SC Wiki blueprint sync: {} blueprints upserted, {} unresolved ingredient"
            + " line(s), {} detail fetch miss(es).",
        processed,
        unresolvedLines,
        detailMisses);
  }

  /**
   * Copies the detail's dismantle time / efficiency onto the blueprint. No-op when the detail omits
   * the {@code dismantle} block (e.g. on a list-only fallback row).
   *
   * @param bp the managed blueprint
   * @param dto the inbound blueprint DTO (detail or list fallback)
   */
  private void applyDismantle(Blueprint bp, ScWikiBlueprintDto dto) {
    if (dto.dismantle() != null) {
      bp.setDismantleTimeSeconds(dto.dismantle().timeSeconds());
      bp.setDismantleEfficiency(dto.dismantle().efficiency());
    }
  }

  /**
   * Rebuilds the blueprint's requirement-group graph (slots + stat modifiers), its ingredient lines
   * (from the group children, each linked to its slot) and its summary-property roll-up from the
   * detail payload. The three owned collections are cleared first; orphan removal deletes the
   * previous generation on flush. Each ingredient child resolves to a material (RESOURCE) or game
   * item (ITEM); an unresolved line keeps its Wiki snapshot, logs {@link
   * SyncEventType#UNRESOLVED_INGREDIENT}, and is counted in the return value.
   *
   * @param bp the managed blueprint
   * @param dto the inbound blueprint detail DTO (requirement groups guaranteed non-empty by caller)
   * @param runId current run id for unresolved-ingredient events
   * @return number of ingredient lines that could not be resolved to a material / game item
   */
  private int applyRequirementGraph(Blueprint bp, ScWikiBlueprintDto dto, UUID runId) {
    bp.clearIngredients();
    bp.clearRequirementGroups();
    bp.clearSummaryProperties();

    int unresolved = 0;
    int groupOrder = 0;
    int ingredientOrder = 0;

    for (ScWikiBlueprintRequirementGroupDto g : dto.requirementGroups()) {
      BlueprintRequirementGroup group = new BlueprintRequirementGroup();
      group.setOrderIndex(groupOrder++);
      group.setGroupKey(g.key());
      group.setName(g.name());
      group.setKind(g.kind());
      group.setRequiredCount(g.requiredCount());
      bp.addRequirementGroup(group);

      if (g.modifiers() != null) {
        int modifierOrder = 0;
        for (ScWikiBlueprintModifierDto m : g.modifiers()) {
          BlueprintRequirementModifier modifier = new BlueprintRequirementModifier();
          modifier.setOrderIndex(modifierOrder++);
          modifier.setPropertyKey(m.propertyKey());
          modifier.setLabel(m.label());
          modifier.setBetterWhen(m.betterWhen());
          if (m.qualityRange() != null) {
            modifier.setQualityMin(m.qualityRange().min());
            modifier.setQualityMax(m.qualityRange().max());
          }
          if (m.modifierRange() != null) {
            modifier.setModifierAtMinQuality(m.modifierRange().atMinQuality());
            modifier.setModifierAtMaxQuality(m.modifierRange().atMaxQuality());
          }
          modifier.setValueRangeType(m.valueRangeType());
          if (m.valueSegments() != null) {
            int segmentOrder = 0;
            for (ScWikiBlueprintModifierSegmentDto seg : m.valueSegments()) {
              BlueprintModifierSegment segment = new BlueprintModifierSegment();
              segment.setOrderIndex(segmentOrder++);
              segment.setQualityMin(seg.qualityMin());
              segment.setQualityMax(seg.qualityMax());
              segment.setModifierAtStart(seg.modifierAtStart());
              segment.setModifierAtEnd(seg.modifierAtEnd());
              modifier.addSegment(segment);
            }
          }
          group.addModifier(modifier);
        }
      }

      if (g.children() != null) {
        for (ScWikiBlueprintRequirementChildDto child : g.children()) {
          if (applyChild(bp, group, child, ingredientOrder++, dto.uuid(), runId)) {
            unresolved++;
          }
        }
      }
    }

    if (dto.summaryProperties() != null) {
      int summaryOrder = 0;
      for (ScWikiBlueprintSummaryPropertyDto sp : dto.summaryProperties()) {
        BlueprintSummaryProperty summary = new BlueprintSummaryProperty();
        summary.setOrderIndex(summaryOrder++);
        summary.setPropertyKey(sp.propertyKey());
        summary.setLabel(sp.label());
        summary.setBetterWhen(sp.betterWhen());
        bp.addSummaryProperty(summary);
      }
    }
    return unresolved;
  }

  /**
   * Builds one ingredient line from a requirement-group child, links it to its slot, resolves the
   * material / game item reference, and appends it to the blueprint. Logs an unresolved event when
   * the reference cannot be resolved.
   *
   * @param bp the managed blueprint
   * @param group the owning requirement group (slot)
   * @param child the inbound child line
   * @param orderIndex the line's position within the blueprint's ingredient list
   * @param blueprintUuid the owning blueprint's Wiki UUID (for the unresolved event)
   * @param runId current run id for the unresolved event
   * @return {@code true} if the line could not be resolved (so the caller increments its counter)
   */
  private boolean applyChild(
      Blueprint bp,
      BlueprintRequirementGroup group,
      ScWikiBlueprintRequirementChildDto child,
      int orderIndex,
      UUID blueprintUuid,
      UUID runId) {
    BlueprintIngredient line = new BlueprintIngredient();
    line.setOrderIndex(orderIndex);
    line.setRequirementGroup(group);
    line.setWikiNameSnapshot(child.name());
    line.setMinQuality(child.minQuality());

    boolean unresolved;
    if ("item".equalsIgnoreCase(child.kind())) {
      line.setKind(BlueprintIngredientKind.ITEM);
      line.setWikiItemUuid(child.uuid());
      line.setQuantityUnits(child.quantity());
      GameItem resolved = resolveGameItem(child.uuid());
      line.setGameItem(resolved);
      unresolved = resolved == null;
    } else {
      line.setKind(BlueprintIngredientKind.RESOURCE);
      line.setWikiResourceUuid(child.uuid());
      line.setQuantityScu(child.quantityScu());
      Material resolved = resolveMaterialForChild(child);
      line.setMaterial(resolved);
      unresolved = resolved == null;
    }
    bp.addIngredient(line);
    if (unresolved) {
      logUnresolved(runId, blueprintUuid, child.name());
    }
    return unresolved;
  }

  /**
   * Reconciles a blueprint's ingredient lines against the inbound flat list (fallback path when no
   * detail / requirement groups are available): reuses existing lines by index, appends new ones,
   * and drops trailing lines the upstream recipe no longer has (orphan removal deletes them on
   * flush). Returns the count of lines left unresolved this pass.
   *
   * @param bp the managed blueprint
   * @param dto the inbound blueprint DTO
   * @param runId current run id for unresolved-ingredient events
   * @return number of ingredient lines that could not be resolved to a material / game item
   */
  private int applyIngredients(Blueprint bp, ScWikiBlueprintDto dto, UUID runId) {
    List<ScWikiBlueprintIngredientDto> incoming =
        dto.ingredients() == null ? List.of() : dto.ingredients();
    List<BlueprintIngredient> lines = bp.getIngredients();
    int unresolved = 0;

    for (int i = 0; i < incoming.size(); i++) {
      ScWikiBlueprintIngredientDto ing = incoming.get(i);
      BlueprintIngredient line;
      if (i < lines.size()) {
        line = lines.get(i);
      } else {
        line = new BlueprintIngredient();
        bp.addIngredient(line);
      }
      line.setOrderIndex(i);
      line.setWikiResourceUuid(ing.resourceTypeUuid());
      line.setWikiItemUuid(ing.itemUuid());
      line.setWikiNameSnapshot(ing.name());

      boolean isItem = "item".equalsIgnoreCase(ing.kind());
      if (isItem) {
        line.setKind(BlueprintIngredientKind.ITEM);
        line.setQuantityUnits(ing.quantity());
        line.setQuantityScu(null);
        line.setMaterial(null);
        GameItem resolved = resolveGameItem(ing.itemUuid());
        line.setGameItem(resolved);
        if (resolved == null) {
          logUnresolved(runId, dto.uuid(), ing.name());
          unresolved++;
        }
      } else {
        line.setKind(BlueprintIngredientKind.RESOURCE);
        line.setQuantityScu(ing.quantityScu());
        line.setQuantityUnits(null);
        line.setGameItem(null);
        Material resolved = resolveMaterialForResource(ing);
        line.setMaterial(resolved);
        if (resolved == null) {
          logUnresolved(runId, dto.uuid(), ing.name());
          unresolved++;
        }
      }
    }

    while (lines.size() > incoming.size()) {
      bp.removeLastIngredient();
    }
    return unresolved;
  }

  /**
   * Reconciles a blueprint's dismantle-return lines (RESOURCE-only) against the inbound DTO, in the
   * same reuse-by-index / drop-trailing manner as {@link #applyIngredients}.
   *
   * @param bp the managed blueprint
   * @param dto the inbound blueprint DTO
   * @param runId current run id for unresolved events
   */
  private void applyDismantleReturns(Blueprint bp, ScWikiBlueprintDto dto, UUID runId) {
    List<ScWikiBlueprintIngredientDto> incoming =
        dto.dismantleReturns() == null ? List.of() : dto.dismantleReturns();
    List<BlueprintDismantleReturn> lines = bp.getDismantleReturns();

    for (int i = 0; i < incoming.size(); i++) {
      ScWikiBlueprintIngredientDto ret = incoming.get(i);
      BlueprintDismantleReturn line;
      if (i < lines.size()) {
        line = lines.get(i);
      } else {
        line = new BlueprintDismantleReturn();
        bp.addDismantleReturn(line);
      }
      line.setOrderIndex(i);
      line.setWikiResourceUuid(ret.resourceTypeUuid());
      line.setWikiNameSnapshot(ret.name());
      line.setQuantityScu(ret.quantityScu());
      Material resolved = resolveMaterialForResource(ret);
      line.setMaterial(resolved);
      if (resolved == null) {
        logUnresolved(runId, dto.uuid(), ret.name());
      }
    }

    while (lines.size() > incoming.size()) {
      bp.removeLastDismantleReturn();
    }
  }

  /**
   * Resolves a RESOURCE ingredient / dismantle-return reference to a local material: {@code
   * scwiki_uuid} → alias table → exact name. Returns {@code null} when none match (the caller
   * persists the raw Wiki snapshot and logs the miss).
   *
   * @param ref the inbound flat ingredient / return line
   * @return the resolved material, or {@code null}
   */
  private Material resolveMaterialForResource(ScWikiBlueprintIngredientDto ref) {
    return resolveMaterial(ref.resourceTypeUuid(), ref.name());
  }

  /**
   * Resolves a RESOURCE requirement-group child to a local material, using the child's {@code uuid}
   * (the resource-type UUID) and display name through the same {@code scwiki_uuid} → alias → exact
   * name chain as {@link #resolveMaterialForResource}.
   *
   * @param child the inbound requirement-group child line
   * @return the resolved material, or {@code null}
   */
  private Material resolveMaterialForChild(ScWikiBlueprintRequirementChildDto child) {
    return resolveMaterial(child.uuid(), child.name());
  }

  /**
   * Shared material resolution: {@code scwiki_uuid} → alias table → exact name. Returns {@code
   * null} when none match.
   *
   * @param resourceTypeUuid the Wiki resource-type UUID, or {@code null}
   * @param name the Wiki display name, or {@code null}
   * @return the resolved material, or {@code null}
   */
  private Material resolveMaterial(UUID resourceTypeUuid, String name) {
    if (resourceTypeUuid != null) {
      var byUuid = materialRepository.findByScwikiUuid(resourceTypeUuid);
      if (byUuid.isPresent()) {
        return byUuid.orElseThrow();
      }
    }
    Material byAlias =
        aliasService.resolveMaterialByAlias(MaterialExternalAliasSource.SCWIKI, name);
    if (byAlias != null) {
      return byAlias;
    }
    if (StringUtils.hasText(name)) {
      return materialRepository.findByName(name).orElse(null);
    }
    return null;
  }

  /**
   * Resolves an ITEM reference (or a blueprint output) to a local game item by shared {@code
   * external_uuid}.
   *
   * @param itemUuid the Wiki item UUID, or {@code null}
   * @return the resolved game item, or {@code null}
   */
  private GameItem resolveGameItem(UUID itemUuid) {
    if (itemUuid == null) {
      return null;
    }
    return gameItemRepository.findByExternalUuid(itemUuid).orElse(null);
  }

  /**
   * Logs an {@link SyncEventType#UNRESOLVED_INGREDIENT} event for a line whose material / item
   * reference could not be resolved this run.
   *
   * @param runId current run id
   * @param blueprintUuid the owning blueprint's Wiki UUID
   * @param ingredientName the Wiki ingredient name
   */
  private void logUnresolved(UUID runId, UUID blueprintUuid, String ingredientName) {
    syncReportService.logScwikiEvent(
        runId,
        SyncEventType.UNRESOLVED_INGREDIENT,
        "blueprint",
        blueprintUuid,
        ingredientName,
        "Ingredient '"
            + (ingredientName == null ? "?" : ingredientName)
            + "' not resolvable to a material / game item — persisted via Wiki snapshot for "
            + "re-resolution on a later run.");
  }
}
