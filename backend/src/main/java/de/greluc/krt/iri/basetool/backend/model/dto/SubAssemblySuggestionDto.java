package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;

/**
 * One blueprint ITEM (sub-assembly) ingredient surfaced to the create UI as an adoptable line: the
 * sub-item to craft, the quantity needed for the previewed amount, and the blueprints that can
 * produce it (for the per-line blueprint pick when adopted). Adopting a suggestion adds a child
 * item line whose own RESOURCE ingredients then feed the order's material aggregation (issue #304
 * decision 1).
 *
 * @param gameItem the sub-assembly item to craft
 * @param quantity whole-unit count needed for the previewed amount
 * @param blueprints the blueprints that produce {@code gameItem}; empty when none are known
 */
public record SubAssemblySuggestionDto(
    GameItemReferenceDto gameItem, Integer quantity, List<BlueprintReferenceDto> blueprints) {}
