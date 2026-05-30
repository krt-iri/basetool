package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Boundary DTO for a synced crafting blueprint on the admin blueprint page. Carries the recipe
 * header, the requirement groups (build slots) with their ingredients and stat modifiers, the flat
 * ingredient list (rendered as a fallback for blueprints synced before their detail / requirement
 * groups were captured), the de-duplicated stat roll-up, and the dismantle returns.
 *
 * @param id local primary key
 * @param scwikiUuid SC Wiki blueprint UUID
 * @param scwikiKey Wiki internal key (e.g. {@code "BP_CRAFT_AMRS_LaserCannon_S1"})
 * @param outputName display name of the crafted item
 * @param craftTimeSeconds craft time in seconds
 * @param isAvailableByDefault whether the recipe is unlocked by default
 * @param ingredientCount Wiki-reported distinct ingredient count
 * @param unlockingMissionsCount Wiki-reported count of missions that unlock the recipe
 * @param gameVersionSeen game version the recipe was last seen in
 * @param dismantleTimeSeconds dismantle duration in seconds, or {@code null}
 * @param dismantleEfficiency fraction of inputs recovered on dismantle, or {@code null}
 * @param scwikiSyncedAt timestamp of the last successful SC Wiki sync touch
 * @param requirementGroups build slots with their ingredients and stat modifiers
 * @param ingredients the flat ingredient list (fallback rendering when no groups are present)
 * @param summaryProperties roll-up of the stats this blueprint affects
 * @param dismantleReturns commodities recovered on dismantle
 * @param version optimistic-lock version
 */
public record BlueprintDto(
    UUID id,
    UUID scwikiUuid,
    String scwikiKey,
    String outputName,
    Integer craftTimeSeconds,
    @JsonProperty("isAvailableByDefault") Boolean isAvailableByDefault,
    Integer ingredientCount,
    Integer unlockingMissionsCount,
    String gameVersionSeen,
    Integer dismantleTimeSeconds,
    Double dismantleEfficiency,
    Instant scwikiSyncedAt,
    List<BlueprintRequirementGroupDto> requirementGroups,
    List<BlueprintRequirementIngredientDto> ingredients,
    List<BlueprintSummaryPropertyDto> summaryProperties,
    List<BlueprintDismantleReturnDto> dismantleReturns,
    Long version) {}
