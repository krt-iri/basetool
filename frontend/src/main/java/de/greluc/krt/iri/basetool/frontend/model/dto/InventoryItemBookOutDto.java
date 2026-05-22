package de.greluc.krt.iri.basetool.frontend.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Frontend mirror of the backend {@code InventoryItemBookOutDto} (per the {@code
 * feedback_backend_frontend_dto_mirror} memory).
 *
 * <p>R5.d.g added the trailing {@code targetOwningOrgUnitId} picker output. Only honoured for
 * {@link CheckoutType#TRANSFER}; ignored for {@link CheckoutType#DISCARD} and {@link
 * CheckoutType#SELL}.
 */
public record InventoryItemBookOutDto(
    @NotNull @Min(0) Double amount,
    UUID targetUserId,
    UUID targetLocationId,
    CheckoutType type,
    String terminal,
    @Min(0) BigDecimal sellAmount,
    @NotNull Long version,
    UUID targetOwningOrgUnitId) {}
