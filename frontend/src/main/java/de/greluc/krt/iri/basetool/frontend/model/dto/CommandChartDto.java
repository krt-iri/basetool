package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Frontend mirror of one Kommando within a Staffel.
 *
 * @param commandLead the Kommandoleiter node; never {@code null}.
 * @param deputy the Stv. Kommandoleiter node, or {@code null} when vacant.
 * @param ensigns the Ensigns reporting into this command; never {@code null}, possibly empty.
 */
public record CommandChartDto(
    OrgChartNodeDto commandLead, OrgChartNodeDto deputy, List<OrgChartNodeDto> ensigns) {}
