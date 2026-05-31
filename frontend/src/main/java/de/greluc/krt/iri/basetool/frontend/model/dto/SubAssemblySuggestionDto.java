package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Frontend mirror of the backend {@code SubAssemblySuggestionDto}: a blueprint ITEM ingredient the
 * requester may adopt as a further item line.
 *
 * @param gameItem the sub-assembly item to craft
 * @param quantity whole-unit count needed for the previewed amount
 * @param blueprints the blueprints that produce {@code gameItem}
 */
public record SubAssemblySuggestionDto(
    GameItemReferenceDto gameItem, Integer quantity, List<BlueprintReferenceDto> blueprints) {}
