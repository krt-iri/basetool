package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/**
 * Narrow reference projection of a {@code Squadron} carrying only the fields the UI needs to render
 * a badge or a list column: the surrogate id, the long-form name (for tooltips / full-width cells),
 * and the short three- or four-letter handle ({@code shorthand}) the corporate design uses on
 * chips. Embedded into the per-aggregate list / detail DTOs (Mission, JobOrder, Inventory,
 * Refinery, Operation, Ship) so the squadron column / badge can be rendered without a separate
 * lookup. Deliberately omits {@code active} / {@code description} / {@code version} - those belong
 * on the full {@link SquadronDto} used by the admin squadron-management page.
 */
public record SquadronReferenceDto(UUID id, String name, String shorthand) {}
