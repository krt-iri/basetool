package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;

/**
 * One Kommando within a Staffel: the Kommandoleiter, its optional Stv. Kommandoleiter, and the
 * Ensigns reporting into this command. Up to four of these hang under a {@link SquadronChartDto}.
 *
 * @param commandLead the Kommandoleiter node; never {@code null} (a Kommando only exists once its
 *     lead is assigned).
 * @param deputy the Stv. Kommandoleiter node, or {@code null} when no deputy is assigned.
 * @param ensigns the Ensigns reporting into this command, ordered for display; never {@code null},
 *     possibly empty.
 */
public record CommandChartDto(
    OrgChartNodeDto commandLead, OrgChartNodeDto deputy, List<OrgChartNodeDto> ensigns) {}
