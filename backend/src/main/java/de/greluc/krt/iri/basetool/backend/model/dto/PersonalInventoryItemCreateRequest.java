package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PersonalInventoryLocationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Write DTO for creating a personal inventory entry. The location display name is NOT accepted from
 * the client; the server resolves it from the local UEX City/Space-Station mirror and persists a
 * snapshot to keep the entry renderable offline.
 */
public record PersonalInventoryItemCreateRequest(
    @NotBlank @Size(max = 120) String name,
    @Size(max = 2000) String note,
    @NotNull Integer locationUexId,
    @NotNull PersonalInventoryLocationType locationType,
    @NotNull @Min(1) Integer quantity) {}
