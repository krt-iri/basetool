package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import de.greluc.krt.iri.basetool.backend.model.CheckoutType;

public record InventoryItemBookOutDto(
    @NotNull @Min(0) Double amount,
    UUID targetUserId,
    UUID targetLocationId,
    CheckoutType type,
    String terminal,
    @Min(0) BigDecimal sellAmount,
    @NotNull Long version
) {}
