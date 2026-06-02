package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Frontend mirror of the backend {@code PersonalBlueprintRecipeResponse}: the SC Wiki recipe graph
 * (build slots with ingredients + per-quality stat modifiers) of a single owned blueprint's
 * product, consumed by the Personal Inventory blueprint view's expandable "Zutaten &amp; Stats"
 * detail (#327).
 *
 * @param productName canonical display name of the product
 * @param variantCount number of recipe variants collapsing into the product
 * @param requirementGroups the representative recipe's build slots with ingredients + stat
 *     modifiers
 * @param ingredients flat ingredient list used as the fallback when {@code requirementGroups} is
 *     empty
 */
public record PersonalBlueprintRecipeDto(
    String productName,
    int variantCount,
    List<BlueprintRequirementGroupDto> requirementGroups,
    List<BlueprintRequirementIngredientDto> ingredients) {}
