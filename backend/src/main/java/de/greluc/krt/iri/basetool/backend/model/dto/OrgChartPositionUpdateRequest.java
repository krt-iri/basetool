package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Inbound payload for {@code PUT /api/v1/org-chart/positions/{id}} — edits an existing position.
 * The functional rank, the scope (OrgUnit) and the parent are immutable after creation — moving a
 * position to a different parent is done by removing it and re-adding it — so only the holder and
 * the display order may change. A {@code null} field on the wire means "leave unchanged".
 *
 * @param userId the new holder, or {@code null} to keep the current one.
 * @param sortIndex the new display order, or {@code null} to keep the current one.
 * @param version current optimistic-lock version held by the client; required so concurrent edits
 *     surface as a 409.
 */
public record OrgChartPositionUpdateRequest(
    UUID userId, Integer sortIndex, @NotNull Long version) {}
