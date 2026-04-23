package de.greluc.krt.iri.basetool.frontend.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;

/**
 * Request payload to create, update or remove the free-text note on an inventory item.
 * A blank or empty {@code note} removes the existing note. {@code version} carries the entity
 * version for optimistic locking.
 */
public record InventoryItemNoteUpdateRequest(
    @Nullable @Size(max = 1000) String note,
    @NotNull Long version
) {}
