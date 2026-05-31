package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Create payload for an {@code ITEM} job order. Mirrors {@link CreateJobOrderDto}'s org-unit
 * stamping contract ({@code creatingSquadronId} immutable/optional, {@code requestingOrgUnitId}
 * optional with creating-squadron fallback) but carries finished-item lines instead of raw
 * materials. The required materials are derived and snapshotted from each line's blueprint server-
 * side; the client never sends quantities, only the per-material Gut/Keine choices.
 *
 * @param creatingSquadronId optional, immutable author stamp; {@code null} = caller's active
 *     squadron context (admins in all-squadrons mode must set it explicitly)
 * @param requestingOrgUnitId optional org unit the order executes for; {@code null} falls back to
 *     the creating squadron
 * @param handle optional contact handle (≤ 200 chars)
 * @param comment optional free-text note (≤ 1000 chars), HTML-escaped on display
 * @param items the ordered finished-item lines (1..50)
 * @param version optimistic-lock version (unused on create)
 */
public record CreateJobOrderItemRequestDto(
    @Nullable UUID creatingSquadronId,
    @Nullable UUID requestingOrgUnitId,
    @Size(max = 200) String handle,
    @Size(max = 1000) String comment,
    @NotEmpty @Size(max = 50) @Valid List<CreateJobOrderItemLineDto> items,
    Long version) {}
