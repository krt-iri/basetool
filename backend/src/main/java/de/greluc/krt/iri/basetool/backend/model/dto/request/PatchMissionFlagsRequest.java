package de.greluc.krt.iri.basetool.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for a partial update of the flags section of a mission ({@code isInternal}).
 *
 * <p>The {@code version} field is the dedicated {@code mission.flags_version} section counter — not
 * the global {@code Mission.@Version}. Concurrent edits on the core or schedule section therefore
 * never invalidate a flags patch in flight (and vice versa).
 */
public record PatchMissionFlagsRequest(@NotNull Boolean isInternal, @NotNull Long version) {}
