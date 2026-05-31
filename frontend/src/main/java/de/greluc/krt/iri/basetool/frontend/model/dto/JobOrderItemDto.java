package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;
import java.util.UUID;

/**
 * Frontend mirror of the backend {@code JobOrderItemDto}: one ordered finished-item line of an item
 * order.
 *
 * @param id the item-line id
 * @param gameItem the requested finished item
 * @param blueprint the chosen recipe
 * @param amount requested whole-unit count
 * @param deliveredAmount whole units already handed over
 * @param parentItemId the parent line this was adopted from, or {@code null}
 * @param materials the snapshotted material requirements
 * @param version optimistic-lock version
 */
public record JobOrderItemDto(
    UUID id,
    GameItemReferenceDto gameItem,
    BlueprintReferenceDto blueprint,
    Integer amount,
    Integer deliveredAmount,
    UUID parentItemId,
    List<JobOrderItemMaterialDto> materials,
    Long version) {}
