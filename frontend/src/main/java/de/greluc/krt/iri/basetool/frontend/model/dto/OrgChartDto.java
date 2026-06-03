package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Frontend mirror of the complete Profit-Bereich org chart, decoded from {@code GET
 * /api/v1/org-chart}.
 *
 * @param areaLeadership the Bereichsleitung tier; never {@code null}.
 * @param squadrons the profit-eligible Staffeln, ordered by name; never {@code null}.
 * @param specialCommands the profit-eligible Spezialkommandos, ordered by name; never {@code null}.
 */
public record OrgChartDto(
    AreaLeadershipDto areaLeadership,
    List<SquadronChartDto> squadrons,
    List<SpecialCommandChartDto> specialCommands) {}
