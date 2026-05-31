package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;
import java.util.UUID;

/**
 * One ordered finished-item line of an item job order: the requested {@code gameItem}, the {@code
 * blueprint} chosen to produce it, the requested and already-delivered unit counts, and the
 * snapshotted per-material requirements. {@code parentItemId} is non-null when the line was adopted
 * from another line's blueprint sub-assembly suggestion (provenance).
 *
 * @param id the item-line primary key
 * @param gameItem the requested finished item
 * @param blueprint the recipe chosen for this line
 * @param amount requested whole-unit count
 * @param deliveredAmount whole units already handed over
 * @param parentItemId the parent line this was adopted from, or {@code null} for a top-level line
 * @param materials the snapshotted material requirements for this line
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
