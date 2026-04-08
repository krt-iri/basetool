package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PayoutPreference;
import java.time.Instant;
import java.util.UUID;

public record UpdateParticipantRequest(
        UUID desiredMissionJobTypeId,
        UUID plannedMissionJobTypeId,
        String comment,
        Instant startTime,
        Instant endTime,
        UUID squadronId,
        PayoutPreference payoutPreference,
        Long version
) {
}
