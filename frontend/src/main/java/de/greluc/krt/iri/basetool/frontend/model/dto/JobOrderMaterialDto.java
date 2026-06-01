package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;
import java.util.UUID;

/**
 * Frontend mirror of the backend {@code JobOrderMaterialDto}. {@code claims}/{@code openAmount} are
 * populated only for public SK orders (Phase 5, #345); {@code openAmount} is {@code null} for
 * private orders, which is how the detail template decides whether to render the claim columns.
 *
 * @param id material-line primary key
 * @param material the required material (carries {@code quantityType} for unit-aware display)
 * @param minQuality the minimum acceptable quality (700) or {@code null} for "Keine"
 * @param amount the required amount
 * @param currentStock the summed linked-inventory stock
 * @param claims the per-squadron claims on this bucket (empty for non-SK orders)
 * @param openAmount {@code required − Σ claims}; {@code null} for non-SK orders
 * @param version optimistic-lock version
 */
public record JobOrderMaterialDto(
    UUID id,
    MaterialDto material,
    Integer minQuality,
    Double amount,
    Double currentStock,
    List<ClaimDto> claims,
    Double openAmount,
    Long version) {}
