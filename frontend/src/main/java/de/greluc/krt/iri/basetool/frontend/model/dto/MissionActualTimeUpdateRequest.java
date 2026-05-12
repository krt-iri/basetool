package de.greluc.krt.iri.basetool.frontend.model.dto;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Request DTO for setting the actual start/end time of a mission
 * via the "Now" buttons on the mission detail page.
 *
 * <p>The {@code version} field is mandatory so that Spring Data JPA's optimistic locking
 * ({@code ObjectOptimisticLockingFailureException}) engages on concurrent changes and the
 * endpoint can respond with HTTP 409. Validation (allowed values for {@code field},
 * presence of {@code version}) is performed in the controller to consistently return
 * HTTP 400.</p>
 *
 * @param field   Name of the field to update ({@code actualStartTime} or {@code actualEndTime}).
 * @param value   UTC instant to set ({@link Instant}). {@code null} clears the value.
 * @param version Current entity version (optimistic locking).
 */
public record MissionActualTimeUpdateRequest(
        @Nullable String field,
        @Nullable Instant value,
        @Nullable Long version
) {
}
