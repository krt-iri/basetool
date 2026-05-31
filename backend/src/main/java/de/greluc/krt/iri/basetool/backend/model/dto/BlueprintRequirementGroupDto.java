package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;

/**
 * Boundary DTO for one named build slot of a blueprint, bundling the ingredient(s) that fill the
 * slot ({@link #ingredients}) with the stat contributions that slot makes to the crafted item
 * ({@link #modifiers}). This is the unit the admin blueprint page renders to show "which ingredient
 * delivers which stat".
 *
 * @param name display name of the slot (e.g. {@code "Emitter"})
 * @param groupKey Wiki internal key of the slot (e.g. {@code "EMITTER"})
 * @param requiredCount number of children that must be fulfilled within the slot
 * @param modifiers the stat contributions of this slot
 * @param ingredients the ingredient(s) that fill this slot
 */
public record BlueprintRequirementGroupDto(
    String name,
    String groupKey,
    Integer requiredCount,
    List<BlueprintRequirementModifierDto> modifiers,
    List<BlueprintRequirementIngredientDto> ingredients) {}
