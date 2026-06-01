package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Frontend mirror of the backend {@code ItemDerivationDto}: the material-derivation preview for one
 * blueprint at a given amount, feeding the item-order create form.
 *
 * @param blueprint the previewed blueprint
 * @param amount the previewed whole-unit amount
 * @param materials the resolved material requirements
 * @param subAssemblies adoptable sub-assembly suggestions
 * @param unresolvedIngredients names of ingredient lines with no resolved material/item
 */
public record ItemDerivationDto(
    BlueprintReferenceDto blueprint,
    Integer amount,
    List<DerivedMaterialDto> materials,
    List<SubAssemblySuggestionDto> subAssemblies,
    List<String> unresolvedIngredients) {}
