package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/** Frontend mirror of the backend blueprint requirement-group DTO (a build slot + its stats). */
public record BlueprintRequirementGroupDto(
    String name,
    String groupKey,
    Integer requiredCount,
    List<BlueprintRequirementModifierDto> modifiers,
    List<BlueprintRequirementIngredientDto> ingredients) {}
