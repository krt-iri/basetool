package de.greluc.krt.iri.basetool.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.jetbrains.annotations.Nullable;

/**
 * Request DTO for a partial update of the schedule section of a mission.
 *
 * <p>All timestamps are accepted and stored in UTC ({@link Instant}). Display in the frontend uses
 * the user's local timezone.
 *
 * <p>The {@code version} field is the dedicated {@code mission.schedule_version} section counter —
 * not the global {@code Mission.@Version}. Concurrent edits on the core or flags section therefore
 * never invalidate a schedule patch in flight (and vice versa).
 */
public record PatchMissionScheduleRequest(
    @Nullable Instant meetingTime,
    @Nullable Instant plannedStartTime,
    @Nullable Instant plannedEndTime,
    @Nullable Instant actualStartTime,
    @Nullable Instant actualEndTime,
    @NotNull Long version) {}
