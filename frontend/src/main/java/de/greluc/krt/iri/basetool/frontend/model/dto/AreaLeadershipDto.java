package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Frontend mirror of the Bereichsleitung tier. Any list may be empty and {@link #lead} may be
 * {@code null} when the Bereichsleiter seat is vacant.
 *
 * @param lead the Bereichsleiter node, or {@code null} when vacant.
 * @param commanders the area-leadership Commanders; never {@code null}.
 * @param coordinators the Bereichskoordinatoren; never {@code null}.
 * @param operators the Bereichsoperatoren; never {@code null}.
 */
public record AreaLeadershipDto(
    OrgChartNodeDto lead,
    List<OrgChartNodeDto> commanders,
    List<OrgChartNodeDto> coordinators,
    List<OrgChartNodeDto> operators) {}
