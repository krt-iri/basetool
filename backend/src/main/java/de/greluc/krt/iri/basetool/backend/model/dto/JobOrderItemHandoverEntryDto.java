package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/**
 * One delivered line of an item handover: which ordered item line was fulfilled, the produced item
 * (for display), and how many whole units were handed over.
 *
 * @param id the handover-entry primary key
 * @param jobOrderItemId the ordered item line this entry fulfilled
 * @param gameItem the produced item (slim reference, for the delivery table/PDF)
 * @param amount whole-unit count delivered in this entry
 */
public record JobOrderItemHandoverEntryDto(
    UUID id, UUID jobOrderItemId, GameItemReferenceDto gameItem, Integer amount) {}
