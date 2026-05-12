package de.greluc.krt.iri.basetool.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for a partial update of the flags section of a mission (e.g. isInternal).
 *
 * <p>Section patches allow multiple users to work in parallel on different areas of the
 * mission detail page; thanks to {@code @OptimisticLock(excluded = true)}, sub-changes
 * (participants, units, finance) no longer cause a parent-version collision.
 */
public record PatchMissionFlagsRequest(
        @NotNull Boolean isInternal,
        @NotNull Long version
) {
}
