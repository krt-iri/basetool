package de.greluc.krt.iri.basetool.frontend.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** Data transfer record carrying Inventory Item Book Out payload. */
public record InventoryItemBookOutDto(
    @NotNull @Min(0) Double amount,
    UUID targetUserId,
    UUID targetLocationId,
    CheckoutType type,
    String terminal,
    @Min(0) BigDecimal sellAmount,
    @NotNull Long version) {}
