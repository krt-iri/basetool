package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

import de.greluc.krt.iri.basetool.backend.validation.QuantityAware;
import de.greluc.krt.iri.basetool.backend.validation.ValidQuantityAmount;

/**
 * Request DTO for a single entry in the store dialog of a refinery order.
 *
 * <p>The amount ({@code amount}) is finally set by the user when storing and overrides
 * the output amount originally calculated by the refinery order (see
 * {@code RefineryOrderService#storeRefineryOrder}). Amount validation (decimal number,
 * &gt;= 0, max. 3 decimal places) is uniformly applied across the project via
 * {@link ValidQuantityAmount} / {@link QuantityAware}.
 *
 * <p>The optional {@code note} is propagated directly to the resulting {@code InventoryItem}
 * and lets the user attach remarks already at the time of storage.
 */
@ValidQuantityAmount
public record RefineryOrderStoreItemDto(
    @NotNull UUID materialId,
    @NotNull UUID locationId,
    @NotNull @Min(0) @Max(1000) Integer quality,
    @NotNull Double amount,
    UUID userId,
    UUID jobOrderId,
    @Size(max = 1000) String note
) implements QuantityAware {}
