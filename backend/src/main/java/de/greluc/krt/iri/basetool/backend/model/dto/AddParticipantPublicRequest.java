package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddParticipantPublicRequest(
        UUID userId,
        String guestName,
        @NotNull UUID desiredJobTypeId,
        String comment,
        @NotNull UUID squadronId
) {}
