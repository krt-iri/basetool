package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code JobOrderItemHandoverEntryDto}: one delivered line of an
 * item handover.
 *
 * @param id the handover-entry id
 * @param jobOrderItemId the ordered item line this entry fulfilled
 * @param gameItem the produced item (slim reference)
 * @param amount whole-unit count delivered in this entry
 */
public record JobOrderItemHandoverEntryDto(
    UUID id, UUID jobOrderItemId, GameItemReferenceDto gameItem, Integer amount) {}
