package de.greluc.krt.iri.basetool.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.jetbrains.annotations.Nullable;

/**
 * Request DTO for a partial update of the schedule section of a mission.
 *
 * <p>All timestamps are accepted and stored in UTC ({@link Instant}). Display in the
 * frontend uses the user's local timezone.
 *
 * <p>Optimistic locking is performed via the parent version of the {@code Mission};
 * parallel changes to sub-collections (participants, units, finance) no longer bump
 * the parent version thanks to {@code @OptimisticLock(excluded = true)}.
 */
public record PatchMissionScheduleRequest(
        @Nullable Instant meetingTime,
        @Nullable Instant plannedStartTime,
        @Nullable Instant plannedEndTime,
        @Nullable Instant actualStartTime,
        @Nullable Instant actualEndTime,
        @NotNull Long version
) {
}
