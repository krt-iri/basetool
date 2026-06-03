package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.OrgChartPositionType;
import java.util.UUID;

/**
 * One filled person-node in the rendered org chart: the user who holds a single functional-rank
 * position. Carried inside the nested {@link OrgChartDto} tree (as the Bereichsleiter, a
 * Kommandoleiter, an Ensign, …). The {@code version} and {@code positionId} travel to the client so
 * the inline admin editor can reassign or remove the exact row without a re-fetch.
 *
 * @param positionId id of the underlying {@code org_chart_position} row; the handle every edit /
 *     remove / add-child action targets.
 * @param positionType the functional rank held in this node.
 * @param userId id of the user holding the position; preselects the user in the reassign picker.
 * @param userName the user's effective display name (display name, falling back to username).
 * @param sortIndex stable display order within the sibling group.
 * @param version optimistic-lock version of the row, echoed back on edit to detect concurrent
 *     changes.
 */
public record OrgChartNodeDto(
    UUID positionId,
    OrgChartPositionType positionType,
    UUID userId,
    String userName,
    int sortIndex,
    Long version) {}
