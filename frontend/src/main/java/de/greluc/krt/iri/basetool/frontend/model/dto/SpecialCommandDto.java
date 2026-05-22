package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code SpecialCommandDto}. Mirrors {@link SquadronDto}
 * field-for-field except for the {@code isPromotionEnabled} field — that flag is permanently
 * {@code false} on every Spezialkommando row by the V94 CHECK constraint and the {@code
 * SpecialCommand} setter override, so exposing it on the wire would only let the admin UI receive
 * a constant {@code false} value with no path to flip it.
 *
 * @param id Spezialkommando identifier; nullable on create payloads.
 * @param name display name.
 * @param shorthand short tag used on chips / badges.
 * @param description free-form text; nullable.
 * @param active soft-delete flag; {@code true} for the active reference data.
 * @param version optimistic-lock counter.
 */
public record SpecialCommandDto(
    UUID id,
    String name,
    String shorthand,
    String description,
    Boolean active,
    Long version) {}
