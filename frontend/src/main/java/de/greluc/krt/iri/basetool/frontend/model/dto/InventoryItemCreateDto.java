package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code InventoryItemCreateDto} wire shape. Adding a field on one
 * side without the other surfaces only at render time in production — keep the two records aligned
 * field-for-field, in the same order (see auto-memory {@code
 * feedback_backend_frontend_dto_mirror}).
 *
 * <p>The trailing {@code owningOrgUnitId} field is the R5.d picker output: when non-null, the
 * backend stamps the new inventory row onto the picked org unit instead of the target user's home
 * Staffel. {@code null} preserves the legacy stamping path.
 */
public record InventoryItemCreateDto(
    UUID userId,
    UUID materialId,
    UUID locationId,
    Integer quality,
    Double amount,
    Boolean personal,
    UUID missionId,
    UUID jobOrderId,
    UUID owningOrgUnitId) {}
