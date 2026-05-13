package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.OperationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Data transfer record carrying Operation Create payload. */
public record OperationCreateDto(
    @NotBlank String name, String description, @NotNull OperationStatus status) {}
