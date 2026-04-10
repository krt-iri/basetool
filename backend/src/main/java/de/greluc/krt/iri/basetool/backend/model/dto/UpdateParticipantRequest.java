package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PayoutPreference;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record UpdateParticipantRequest(
        UUID desiredMissionJobTypeId,
        UUID plannedMissionJobTypeId,
        String comment,
        String guestName,
        Instant startTime,
        Instant endTime,
        UUID squadronId,
        PayoutPreference payoutPreference,
        @NotNull Long version
) {
}
