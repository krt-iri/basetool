package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.OrgChartPositionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Inbound payload for {@code POST /api/v1/org-chart/positions} — assigns a user to a new
 * functional-rank position (or creates a Kommando). The combination of {@code positionType}, {@code
 * orgUnitId}, {@code parentId}, {@code userId} and {@code name} is validated against the
 * scope/cardinality/parent rules in {@code OrgChartService}; {@code @Valid} only guarantees {@code
 * positionType} is present and {@code name} stays within its length bound.
 *
 * @param positionType the functional rank to assign; required.
 * @param orgUnitId the owning Staffel/SK; must be {@code null} for area-leadership ranks and
 *     present for Staffel/SK ranks (the service enforces the match).
 * @param userId the user to place in the position. Required for every rank except {@code
 *     COMMAND_LEAD}, where it is optional — omitting it creates a still-leaderless Kommando whose
 *     Kommandoleiter is assigned later. The service rejects a missing {@code userId} for all other
 *     ranks.
 * @param parentId the parent position for a deputy (its Kommandoleiter) or an Ensign (the
 *     Staffelleiter or a Kommandoleiter); {@code null} for every root rank.
 * @param name the Kommando's display name; only honoured for {@code COMMAND_LEAD} (rejected for any
 *     other rank). {@code null} or blank creates an unnamed Kommando.
 * @param sortIndex optional display order within the sibling group; defaults to {@code 0} when
 *     omitted.
 */
public record OrgChartPositionCreateRequest(
    @NotNull OrgChartPositionType positionType,
    UUID orgUnitId,
    UUID userId,
    UUID parentId,
    @Size(max = 120) String name,
    Integer sortIndex) {}
