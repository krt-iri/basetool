package de.greluc.krt.iri.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/**
 * SC Wiki blueprint DTO — a row from {@code /api/blueprints} (SC_WIKI_SYNC_PLAN.md §3.3). The R4
 * {@code ScWikiBlueprintSyncService} consumes the {@link #ingredients} and {@link
 * #dismantleReturns} arrays directly off the list payload (matching the plan's §8.2 pseudocode); if
 * the upstream list endpoint omits them they bind to {@code null} and the blueprint row is still
 * persisted (the sync tolerates absent ingredient arrays and logs how many it resolved).
 *
 * @param uuid SC Wiki blueprint UUID (upsert key)
 * @param key Wiki internal key, e.g. {@code "BP_CRAFT_AMRS_LaserCannon_S1"}
 * @param outputItemUuid authoritative output-item UUID (trust over any nested {@code output.uuid})
 * @param outputName display name of the output item
 * @param categoryUuid Wiki category UUID
 * @param craftTimeSeconds craft time in seconds
 * @param isAvailableByDefault whether the recipe is unlocked by default
 * @param gameVersion game version the row was last seen in
 * @param ingredientCount Wiki-reported ingredient count
 * @param unlockingMissionsCount Wiki-reported unlocking-mission count
 * @param ingredients ordered ingredient lines (may be {@code null} if the list omits them)
 * @param dismantleReturns ordered dismantle-return lines (may be {@code null})
 * @param requirementGroups named build slots, each with its ingredient children and the stat
 *     modifiers that slot contributes to the crafted item. Present only on the blueprint
 *     <em>detail</em> response ({@code GET /api/blueprints/{uuid}}); {@code null} on list payloads.
 * @param summaryProperties de-duplicated roll-up of the stats this blueprint affects (detail
 *     response only; {@code null} on list payloads)
 * @param dismantle dismantle time / efficiency metadata (detail response only; {@code null} on list
 *     payloads)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiBlueprintDto(
    @JsonProperty("uuid") UUID uuid,
    @JsonProperty("key") String key,
    @JsonProperty("output_item_uuid") UUID outputItemUuid,
    @JsonProperty("output_name") String outputName,
    @JsonProperty("category_uuid") UUID categoryUuid,
    @JsonProperty("craft_time_seconds") Integer craftTimeSeconds,
    @JsonProperty("is_available_by_default") Boolean isAvailableByDefault,
    @JsonProperty("game_version") String gameVersion,
    @JsonProperty("ingredient_count") Integer ingredientCount,
    @JsonProperty("unlocking_missions_count") Integer unlockingMissionsCount,
    @JsonProperty("ingredients") List<ScWikiBlueprintIngredientDto> ingredients,
    @JsonProperty("dismantle_returns") List<ScWikiBlueprintIngredientDto> dismantleReturns,
    @JsonProperty("requirement_groups") List<ScWikiBlueprintRequirementGroupDto> requirementGroups,
    @JsonProperty("summary_properties") List<ScWikiBlueprintSummaryPropertyDto> summaryProperties,
    @JsonProperty("dismantle") ScWikiBlueprintDismantleDto dismantle) {}
