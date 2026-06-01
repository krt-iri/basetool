package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;
import java.util.UUID;

/**
 * Data transfer record carrying Job Order Material payload.
 *
 * @param id material-line primary key
 * @param material the required material (with {@code quantityType} for unit-aware display)
 * @param minQuality the minimum acceptable quality (700) or {@code null} for "Keine"
 * @param amount the required amount in the material's own unit
 * @param currentStock the summed linked-inventory stock for this line
 * @param claims the per-squadron claims on this material's bucket; populated only for public SK
 *     orders (Phase 5, #345), empty otherwise
 * @param openAmount {@code required − Σ claims} for the bucket; {@code null} for non-SK orders (no
 *     claim columns are rendered then), a non-null value (possibly 0) for SK orders
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
