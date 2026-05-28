package de.greluc.krt.iri.basetool.backend.integration.scwiki;

import de.greluc.krt.iri.basetool.backend.config.ScWikiProperties;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiBlueprintDto;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiBlueprintIngredientDto;
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
import de.greluc.krt.iri.basetool.backend.repository.BlueprintRepository;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.service.MaterialExternalAliasService;
import de.greluc.krt.iri.basetool.backend.service.SyncReportService;
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
 * blueprint_dismantle_return}.
 *
 * <p>Ingredient resolution: a {@code RESOURCE} line resolves to a {@code material} via {@code
 * scwiki_uuid} → alias table → exact name; an {@code ITEM} line resolves to a {@code game_item} via
 * {@code external_uuid}. The raw Wiki UUID + name snapshot are persisted on every line regardless,
 * so an unresolved line (FK {@code null}, {@link SyncEventType#UNRESOLVED_INGREDIENT} logged)
 * re-resolves on a later run once an alias is added or the item lands in {@code game_item} —
 * without re-fetching the Wiki.
 *
 * <p>Re-sync mutates the blueprint's owned collections in place (reusing lines by index, dropping
 * trailing ones via {@code orphanRemoval}) and relies on Hibernate dirty-checking — no
 * {@code @Modifying} bulk update runs inside the per-blueprint loop, so the CLAUDE.md detach-clear
 * trap does not apply.
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

  private final ScWikiClient scWikiClient;
  private final ScWikiProperties properties;
  private final BlueprintRepository blueprintRepository;
  private final MaterialRepository materialRepository;
  private final MaterialExternalAliasService aliasService;
  private final GameItemRepository gameItemRepository;
  private final SyncReportService syncReportService;

  /**
   * Runs the full blueprint sync. No-op (with an INFO line) when the feature flag is off. An empty
   * Wiki response short-circuits before the orphan sweep so a transient outage never wipes the
   * recipe graph.
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

    for (ScWikiBlueprintDto dto : fetched) {
      if (dto.uuid() == null) {
        continue;
      }
      try {
        seen.add(dto.uuid());
        Blueprint bp = blueprintRepository.findByScwikiUuid(dto.uuid()).orElseGet(Blueprint::new);
        bp.setScwikiUuid(dto.uuid());
        bp.setScwikiKey(dto.key());
        bp.setOutputName(dto.outputName());
        bp.setCategoryUuid(dto.categoryUuid());
        bp.setCraftTimeSeconds(dto.craftTimeSeconds());
        bp.setIsAvailableByDefault(Boolean.TRUE.equals(dto.isAvailableByDefault()));
        bp.setIngredientCount(dto.ingredientCount());
        bp.setUnlockingMissionsCount(dto.unlockingMissionsCount());
        bp.setGameVersionSeen(dto.gameVersion());
        bp.setOutputItem(resolveGameItem(dto.outputItemUuid()));
        unresolvedLines += applyIngredients(bp, dto, runId);
        applyDismantleReturns(bp, dto, runId);
        bp.setScwikiSyncedAt(now);
        bp.setScwikiDeletedAt(null);
        blueprintRepository.save(bp);
        processed++;
      } catch (Exception e) {
        log.error("Failed to process SC Wiki blueprint dto: {}", dto, e);
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
            + " line(s).",
        processed,
        unresolvedLines);
  }

  /**
   * Reconciles a blueprint's ingredient lines against the inbound DTO: reuses existing lines by
   * index, appends new ones, and drops trailing lines the upstream recipe no longer has (orphan
   * removal deletes them on flush). Returns the count of lines left unresolved this pass.
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
        line.setBlueprint(bp);
        lines.add(line);
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
      lines.removeLast();
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
        line.setBlueprint(bp);
        lines.add(line);
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
      lines.removeLast();
    }
  }

  /**
   * Resolves a RESOURCE ingredient / dismantle-return reference to a local material: {@code
   * scwiki_uuid} → alias table → exact name. Returns {@code null} when none match (the caller
   * persists the raw Wiki snapshot and logs the miss).
   *
   * @param ref the inbound ingredient / return line
   * @return the resolved material, or {@code null}
   */
  private Material resolveMaterialForResource(ScWikiBlueprintIngredientDto ref) {
    if (ref.resourceTypeUuid() != null) {
      var byUuid = materialRepository.findByScwikiUuid(ref.resourceTypeUuid());
      if (byUuid.isPresent()) {
        return byUuid.orElseThrow();
      }
    }
    Material byAlias =
        aliasService.resolveMaterialByAlias(MaterialExternalAliasSource.SCWIKI, ref.name());
    if (byAlias != null) {
      return byAlias;
    }
    if (StringUtils.hasText(ref.name())) {
      return materialRepository.findByName(ref.name()).orElse(null);
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
