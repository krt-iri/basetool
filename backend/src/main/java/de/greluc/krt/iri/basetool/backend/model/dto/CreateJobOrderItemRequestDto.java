package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Create payload for an {@code ITEM} job order. Mirrors {@link CreateJobOrderDto}'s org-unit
 * stamping contract ({@code responsibleOrgUnitId} = profit-eligible processor, ignored for guests
 * who are routed to the intake SK; {@code requestingOrgUnitId} = mandatory customer) but carries
 * finished-item lines instead of raw materials. The required materials are derived and snapshotted
 * from each line's blueprint server-side; the client never sends quantities, only the per-material
 * Gut/Keine choices.
 *
 * @param responsibleOrgUnitId the profit-eligible org unit that processes the order; required for
 *     authenticated callers, ignored for guests (routed to the configured intake SK)
 * @param requestingOrgUnitId the customer org unit the order is placed for (any squadron or SK);
 *     mandatory
 * @param handle optional contact handle (≤ 200 chars)
 * @param comment optional free-text note (≤ 1000 chars), HTML-escaped on display
 * @param items the ordered finished-item lines (1..50)
 * @param version optimistic-lock version (unused on create)
 */
public record CreateJobOrderItemRequestDto(
    @Nullable UUID responsibleOrgUnitId,
    @Nullable UUID requestingOrgUnitId,
    @Size(max = 200) String handle,
    @Size(max = 1000) String comment,
    @NotEmpty @Size(max = 50) @Valid List<CreateJobOrderItemLineDto> items,
    Long version) {}
