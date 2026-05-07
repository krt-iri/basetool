package de.greluc.krt.iri.basetool.frontend.model.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating the delivered status of an inventory item.
 * Includes the version field for optimistic locking.
 */
public record UpdateDeliveredRequest(
        @NotNull Boolean delivered,
        @NotNull Long version
) {}
