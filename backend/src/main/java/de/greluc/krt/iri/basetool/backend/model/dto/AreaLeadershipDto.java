package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;

/**
 * The Bereichsleitung tier at the top of the org chart. Holds the single Bereichsleiter plus the
 * open-ended groups of Commander, Bereichskoordinatoren and Bereichsoperatoren. Any list may be
 * empty, and {@link #lead} may be {@code null} when no Bereichsleiter is assigned yet.
 *
 * @param lead the Bereichsleiter node, or {@code null} when the seat is vacant.
 * @param commanders the area-leadership Commanders, ordered for display; never {@code null}.
 * @param coordinators the Bereichskoordinatoren, ordered for display; never {@code null}.
 * @param operators the Bereichsoperatoren, ordered for display; never {@code null}.
 */
public record AreaLeadershipDto(
    OrgChartNodeDto lead,
    List<OrgChartNodeDto> commanders,
    List<OrgChartNodeDto> coordinators,
    List<OrgChartNodeDto> operators) {}
