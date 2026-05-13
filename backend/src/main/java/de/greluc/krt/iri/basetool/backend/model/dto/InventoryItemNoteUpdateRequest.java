package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;

/**
 * Request payload for creating, updating or removing the free-text note on an {@code
 * InventoryItem}.
 *
 * <p>An empty or blank {@code note} value is normalized to {@code null} by the service layer,
 * effectively removing any existing note. The {@code version} field carries the JPA entity version
 * for optimistic locking.
 */
public record InventoryItemNoteUpdateRequest(
    @Nullable @Size(max = 1000) String note, @NotNull Long version) {}
