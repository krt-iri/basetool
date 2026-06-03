package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;

/**
 * The complete Profit-Bereich org chart as one nested read model: the Bereichsleitung on top, then
 * the profit-eligible Staffeln and Spezialkommandos below. Returned by {@code GET
 * /api/v1/org-chart} to every authenticated user; the inline admin editor mutates it one position
 * at a time through the position endpoints.
 *
 * @param areaLeadership the Bereichsleitung tier; never {@code null}.
 * @param squadrons the profit-eligible Staffeln, ordered by name; never {@code null}, possibly
 *     empty.
 * @param specialCommands the profit-eligible Spezialkommandos, ordered by name; never {@code null},
 *     possibly empty.
 */
public record OrgChartDto(
    AreaLeadershipDto areaLeadership,
    List<SquadronChartDto> squadrons,
    List<SpecialCommandChartDto> specialCommands) {}
