package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.JobTypeArchetype;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record JobTypeDto(
        UUID id,
        @NotBlank String name,
        String description,
        @NotNull JobTypeArchetype archetype,
        UUID parentId,
        Boolean active,
        Boolean isLeadershipRole,
        Long version
) {}
