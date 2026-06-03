package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.OrgChartPositionType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Inbound payload for {@code POST /api/v1/org-chart/positions} — assigns a user to a new
 * functional-rank position. The combination of {@code positionType}, {@code orgUnitId} and {@code
 * parentId} is validated against the scope/cardinality/parent rules in {@code OrgChartService};
 * {@code @Valid} only guarantees the two structurally-required fields are present.
 *
 * @param positionType the functional rank to assign; required.
 * @param orgUnitId the owning Staffel/SK; must be {@code null} for area-leadership ranks and
 *     present for Staffel/SK ranks (the service enforces the match).
 * @param userId the user to place in the position; required.
 * @param parentId the parent position for a deputy (its Kommandoleiter) or an Ensign (the
 *     Staffelleiter or a Kommandoleiter); {@code null} for every root rank.
 * @param sortIndex optional display order within the sibling group; defaults to {@code 0} when
 *     omitted.
 */
public record OrgChartPositionCreateRequest(
    @NotNull OrgChartPositionType positionType,
    UUID orgUnitId,
    @NotNull UUID userId,
    UUID parentId,
    Integer sortIndex) {}
