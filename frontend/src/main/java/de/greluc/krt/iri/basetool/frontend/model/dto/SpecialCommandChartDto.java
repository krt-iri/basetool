package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;
import java.util.UUID;

/**
 * Frontend mirror of one Spezialkommando column, led by one or two SK-Leiter (Commander).
 *
 * @param orgUnitId id of the owning Spezialkommando.
 * @param name the SK's display name.
 * @param shorthand the SK's short tag.
 * @param commanders the SK-Leiter nodes; never {@code null}, at most two.
 * @param canAddCommander whether another SK-Leiter may still be added.
 */
public record SpecialCommandChartDto(
    UUID orgUnitId,
    String name,
    String shorthand,
    List<OrgChartNodeDto> commanders,
    boolean canAddCommander) {}
