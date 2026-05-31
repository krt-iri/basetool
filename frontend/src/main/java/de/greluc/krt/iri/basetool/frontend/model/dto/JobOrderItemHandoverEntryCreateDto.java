package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code JobOrderItemHandoverEntryCreateDto}: one delivered line of
 * an item-handover create payload — how many whole units of a specific ordered item line changed
 * hands. The backend rejects an amount exceeding the line's outstanding (ordered minus delivered)
 * quantity.
 *
 * @param jobOrderItemId the ordered item line being fulfilled
 * @param amount whole-unit count delivered for that line (≥ 1)
 */
public record JobOrderItemHandoverEntryCreateDto(UUID jobOrderItemId, Integer amount) {}
