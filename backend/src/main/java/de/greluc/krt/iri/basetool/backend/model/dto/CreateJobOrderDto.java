package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateJobOrderDto(
        @NotBlank String squadron,
        String handle,
        @NotEmpty @Valid List<CreateJobOrderMaterialDto> materials,
        Long version
) {}
