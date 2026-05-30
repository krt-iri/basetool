package de.greluc.krt.iri.basetool.frontend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Frontend mirror of the backend blueprint DTO consumed by the admin blueprint page. */
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
