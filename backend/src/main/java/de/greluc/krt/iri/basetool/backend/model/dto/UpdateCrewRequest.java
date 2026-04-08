package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import java.util.UUID;

public record UpdateCrewRequest(
        @NotEmpty
        Set<UUID> jobTypeIds
) {
}
