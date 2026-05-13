package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for bulk checkout of multiple inventory items. All listed item IDs must belong to the
 * authenticated user.
 */
public record BulkCheckoutRequest(@NotNull @NotEmpty List<@NotNull UUID> itemIds) {}
