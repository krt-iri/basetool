package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record AddCrewRequest(
        @NotNull
        UUID participantId,
        @NotEmpty
        Set<UUID> jobTypeIds
) {
}
