package de.greluc.krt.iri.basetool.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;

/**
 * Request DTO for a partial update of the master data (core section) of a mission.
 *
 * <p>This DTO allows multiple users to work concurrently on different sections of the mission
 * detail page without changes in other sections (e.g. participants, schedule, location) causing
 * input loss in this section. Optimistic locking is applied solely via the {@code version} field of
 * the parent {@code Mission}; sub-collections are decoupled by means of
 * {@code @OptimisticLock(excluded = true)}.
 */
public record PatchMissionCoreRequest(
    @NotBlank @Size(max = 255) String name,
    @Nullable @Size(max = 10000) String description,
    @Nullable @Size(max = 2048) String calendarLink,
    @Nullable @Size(max = 64) String status,
    @NotNull Long version) {}
