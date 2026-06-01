package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Frontend mirror of the backend {@code CreateJobOrderItemRequestDto}: the POST payload for
 * creating an item order via {@code /api/v1/orders/items}.
 *
 * @param responsibleOrgUnitId the profit-eligible org unit that processes the order (ignored for
 *     guests, who are routed to the intake SK)
 * @param requestingOrgUnitId the customer org unit the order is placed for
 * @param handle optional contact handle
 * @param comment optional free-text note
 * @param items the ordered finished-item lines
 * @param version optimistic-lock version (unused on create)
 */
public record CreateJobOrderItemRequestDto(
    @Nullable UUID responsibleOrgUnitId,
    @Nullable UUID requestingOrgUnitId,
    String handle,
    String comment,
    List<CreateJobOrderItemLineDto> items,
    Long version) {}
