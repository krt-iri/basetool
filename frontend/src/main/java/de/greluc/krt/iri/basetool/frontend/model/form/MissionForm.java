package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form-binding object for mission input.
 *
 * <p>{@code version} is the legacy global mission counter and remains for the create-mission flow.
 * On updates of an existing mission, the section-scoped counters {@code coreVersion}, {@code
 * scheduleVersion} and {@code flagsVersion} drive optimistic locking — they enable concurrent users
 * to edit disjoint sections (core / schedule / flags) on the same mission without producing
 * spurious 409 conflicts.
 */
public record MissionForm(
    @NotBlank(message = "{validation.name.required}") @Size(max = 255) String name,
    @Size(max = 2000) String description,
    @Size(max = 2048) String calendarLink,
    @NotBlank(message = "{validation.status.required}") String status,
    String meetingTime,
    String plannedStartTime,
    String plannedEndTime,
    String actualStartTime,
    String actualEndTime,
    Boolean isInternal,
    String operationId,
    Long version,
    Long coreVersion,
    Long scheduleVersion,
    Long flagsVersion) {}
