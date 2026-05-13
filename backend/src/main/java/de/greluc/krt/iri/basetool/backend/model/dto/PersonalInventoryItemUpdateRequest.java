package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PersonalInventoryLocationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Write DTO for updating an existing personal inventory entry. The {@code version} field is
 * mandatory: it carries the entity's last-seen optimistic-locking version, so concurrent
 * modifications are rejected with HTTP 409 (see AGENTS.md "CONCURRENCY AND OPTIMISTIC LOCKING").
 */
public record PersonalInventoryItemUpdateRequest(
    @NotBlank @Size(max = 120) String name,
    @Size(max = 2000) String note,
    @NotNull Integer locationUexId,
    @NotNull PersonalInventoryLocationType locationType,
    @NotNull @Min(1) Integer quantity,
    @NotNull @Min(0) Long version) {}
