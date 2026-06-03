package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Inbound payload for {@code PUT /api/v1/org-chart/positions/{id}} — edits an existing position.
 * The functional rank, the scope (OrgUnit) and the parent are immutable after creation — moving a
 * position to a different parent is done by removing it and re-adding it — so only the holder, the
 * Kommando name and the display order may change. A {@code null} field on the wire means "leave
 * unchanged"; for {@code name}, an empty/blank string clears it back to the unnamed state.
 *
 * @param userId the new holder, or {@code null} to keep the current one. Assigning a holder to a
 *     leaderless Kommando is just a reassign of its {@code COMMAND_LEAD} row through this field.
 * @param name the new Kommando name, or {@code null} to keep the current one; only honoured for a
 *     {@code COMMAND_LEAD} row (rejected otherwise). A blank value clears the name.
 * @param sortIndex the new display order, or {@code null} to keep the current one.
 * @param version current optimistic-lock version held by the client; required so concurrent edits
 *     surface as a 409.
 */
public record OrgChartPositionUpdateRequest(
    UUID userId, @Size(max = 120) String name, Integer sortIndex, @NotNull Long version) {}
