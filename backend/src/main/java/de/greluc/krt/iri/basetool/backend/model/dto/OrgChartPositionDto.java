package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.OrgChartPositionType;
import java.util.UUID;

/**
 * Flat wire shape of a single {@code org_chart_position}, returned by the create / update endpoints
 * so the caller gets the server-stamped id and the bumped {@code version} back. The nested read
 * model uses {@link OrgChartNodeDto} instead; this flat form keeps the write responses simple.
 *
 * @param id the position id (server-stamped on create).
 * @param positionType the functional rank held.
 * @param orgUnitId owning Staffel/SK id, or {@code null} for an area-leadership position.
 * @param userId id of the user holding the position, or {@code null} for a still-leaderless
 *     Kommando ({@code COMMAND_LEAD}).
 * @param userName the user's effective display name, or {@code null} for a leaderless Kommando.
 * @param name the Kommando's display name, or {@code null} (set only on a {@code COMMAND_LEAD}
 *     row).
 * @param parentId id of the parent position (deputy → Kommandoleiter, Ensign → its parent), or
 *     {@code null} for a root position.
 * @param sortIndex stable display order within the sibling group.
 * @param version optimistic-lock version after the write.
 */
public record OrgChartPositionDto(
    UUID id,
    OrgChartPositionType positionType,
    UUID orgUnitId,
    UUID userId,
    String userName,
    String name,
    UUID parentId,
    int sortIndex,
    Long version) {}
