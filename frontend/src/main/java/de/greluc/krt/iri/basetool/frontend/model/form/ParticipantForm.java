package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Form-binding object for Participant input. */
public record ParticipantForm(
    UUID userId,
    @Size(max = 255) String guestName,
    UUID desiredJobTypeId,
    UUID plannedMissionJobTypeId,
    @Size(max = 1000) String comment,
    UUID squadronId,
    String startTime,
    String endTime,
    de.greluc.krt.iri.basetool.frontend.model.PayoutPreference payoutPreference,
    Long version) {}
