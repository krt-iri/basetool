package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Write payload for editing an owned blueprint's mutable fields (#327). The product reference is
 * immutable; only the acquisition date and note can change. {@code version} carries the last seen
 * optimistic-lock version and is mandatory.
 *
 * @param acquiredAt optional in-game acquisition time (cleared when {@code null})
 * @param note optional free-form note (max 2000 chars; cleared when {@code null})
 * @param version the expected optimistic-lock version
 */
public record PersonalBlueprintUpdateRequest(
    Instant acquiredAt, @Size(max = 2000) String note, @NotNull Long version) {}
