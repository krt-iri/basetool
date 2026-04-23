package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

import de.greluc.krt.iri.basetool.backend.validation.QuantityAware;
import de.greluc.krt.iri.basetool.backend.validation.ValidQuantityAmount;

/**
 * Request-DTO fuer einen einzelnen Eintrag im Einlagern-Dialog eines Raffinerieauftrags.
 *
 * <p>Die Menge ({@code amount}) wird beim Einlagern vom Nutzer final festgelegt und
 * ueberschreibt die vom Raffinerieauftrag urspruenglich berechnete Ausgangsmenge
 * (siehe {@code RefineryOrderService#storeRefineryOrder}). Die Validierung der Menge
 * (Dezimalzahl, &gt;= 0, max. 3 Nachkommastellen) ist projektweit einheitlich ueber
 * {@link ValidQuantityAmount} / {@link QuantityAware} abgebildet.
 *
 * <p>Die optionale {@code note} wird direkt an das resultierende {@code InventoryItem}
 * uebernommen und ermoeglicht dem Nutzer, schon beim Einlagern Anmerkungen zu hinterlegen.
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
