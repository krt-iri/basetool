package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;
import java.util.UUID;

/**
 * Frontend mirror of one Staffel column. The {@code canAdd*} flags drive whether the inline editor
 * renders the matching "add" affordance (the backend still enforces the limits authoritatively).
 *
 * @param orgUnitId id of the owning Staffel.
 * @param name the Staffel's display name.
 * @param shorthand the Staffel's short tag.
 * @param lead the Staffelleiter node, or {@code null} when vacant.
 * @param commands the Kommandos; never {@code null}, at most four.
 * @param directEnsigns Ensigns reporting straight to the Staffelleiter; never {@code null}.
 * @param canAddCommand whether another Kommandoleiter may still be added.
 * @param canAddEnsign whether another Ensign may still be added.
 */
public record SquadronChartDto(
    UUID orgUnitId,
    String name,
    String shorthand,
    OrgChartNodeDto lead,
    List<CommandChartDto> commands,
    List<OrgChartNodeDto> directEnsigns,
    boolean canAddCommand,
    boolean canAddEnsign) {}
