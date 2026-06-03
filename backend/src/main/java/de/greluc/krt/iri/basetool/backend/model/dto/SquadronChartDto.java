package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;
import java.util.UUID;

/**
 * One Staffel column in the org chart: its Staffelleiter, up to four Kommandos, and the Ensigns
 * that report directly to the Staffelleiter (rather than into a Kommando). Always rendered even
 * when empty so admins can fill it in. The {@code canAdd*} flags are presentation hints derived
 * from the server-side limits (≤4 Kommandos, ≤4 Ensigns per Staffel) so the inline editor can hide
 * an exhausted "add" button; the service still enforces the limits authoritatively.
 *
 * @param orgUnitId id of the owning Staffel.
 * @param name the Staffel's display name.
 * @param shorthand the Staffel's short tag (e.g. {@code IRI}).
 * @param lead the Staffelleiter node, or {@code null} when the seat is vacant.
 * @param commands the Kommandos, ordered for display; never {@code null}, at most four.
 * @param directEnsigns Ensigns reporting straight to the Staffelleiter, ordered for display; never
 *     {@code null}.
 * @param canAddCommand whether another Kommandoleiter may still be added (fewer than four exist).
 * @param canAddEnsign whether another Ensign may still be added (fewer than four exist in total).
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
