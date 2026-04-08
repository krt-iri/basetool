package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MissionForm(
    @NotBlank(message = "{validation.name.required}") @Size(max=255) String name,
    @Size(max=2000) String description,
    @Size(max=2048) String calendarLink,
    @NotBlank(message = "{validation.status.required}") String status,
    String meetingTime,
    String plannedStartTime,
    String plannedEndTime,
    String actualStartTime,
    String actualEndTime,
    Boolean isInternal,
    String operationId,
    Long version
) {}
