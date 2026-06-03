package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;
import java.util.UUID;

/**
 * One Spezialkommando column in the org chart, led by one or two Commanders (SK-Leiter). SKs carry
 * no further sub-structure in the chart. Always rendered even when empty so admins can fill it in.
 *
 * @param orgUnitId id of the owning Spezialkommando.
 * @param name the SK's display name.
 * @param shorthand the SK's short tag.
 * @param commanders the SK-Leiter nodes, ordered for display; never {@code null}, at most two.
 * @param canAddCommander whether another SK-Leiter may still be added (fewer than two exist).
 */
public record SpecialCommandChartDto(
    UUID orgUnitId,
    String name,
    String shorthand,
    List<OrgChartNodeDto> commanders,
    boolean canAddCommander) {}
