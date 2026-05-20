package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PayoutPreference;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Inbound request payload for the Update Participant operation. {@code @Size} caps mirror the
 * create payload (audit finding H-2) so an anonymous guest editing their own entry cannot grow the
 * row past the create-time limit.
 */
public record UpdateParticipantRequest(
    UUID desiredMissionJobTypeId,
    UUID plannedMissionJobTypeId,
    @Size(max = 1000) String comment,
    @Size(max = 100) String guestName,
    Instant startTime,
    Instant endTime,
    UUID squadronId,
    PayoutPreference payoutPreference,
    @NotNull Long version) {}
