package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;
import java.util.UUID;

/**
 * Frontend mirror of the backend {@code CreateJobOrderItemLineDto}: one ordered finished-item line
 * in the item-order create payload.
 *
 * @param gameItemId the finished item to order
 * @param blueprintId the chosen recipe (must output {@code gameItemId})
 * @param amount whole-unit count (≥ 1)
 * @param materials per-material quality choices
 * @param clientLineId transient client id for provenance linking
 * @param parentClientLineId transient client id of the line this was adopted from
 */
public record CreateJobOrderItemLineDto(
    UUID gameItemId,
    UUID blueprintId,
    Integer amount,
    List<CreateJobOrderItemMaterialDto> materials,
    Integer clientLineId,
    Integer parentClientLineId) {}
