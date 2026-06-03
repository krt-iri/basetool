package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend org-chart node — one filled functional-rank position. The {@code
 * positionType} is carried as the raw enum name (a {@link String}) rather than a duplicated enum,
 * so the template resolves its label via the {@code orgChart.rank.<TYPE>} message key and the
 * inline editor echoes it back unchanged.
 *
 * @param positionId id of the underlying position row; the handle every edit / remove action
 *     targets.
 * @param positionType the functional-rank enum name (e.g. {@code SQUADRON_LEAD}).
 * @param userId id of the user holding the position; preselects the reassign picker.
 * @param userName the holder's effective display name.
 * @param sortIndex stable display order within the sibling group.
 * @param version optimistic-lock version, echoed back on reassign.
 */
public record OrgChartNodeDto(
    UUID positionId,
    String positionType,
    UUID userId,
    String userName,
    int sortIndex,
    Long version) {}
