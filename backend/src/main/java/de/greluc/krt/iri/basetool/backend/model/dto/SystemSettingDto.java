package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Data transfer record carrying System Setting payload. */
public record SystemSettingDto(
    @NotBlank String id, @NotBlank String value, @NotNull Long version) {}
