package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code OrgUnitReferenceDto}. Carries the slim org-unit reference
 * (a Staffel or a Spezialkommando) rendered as a chip in a mission participant's affiliation
 * column. Embedded as a list into {@link MissionParticipantDto} so a participant affiliated with
 * both a Staffel and one or more Spezialkommandos renders all of its badges.
 *
 * @param id surrogate id of the org unit.
 * @param name long-form display name (tooltip / full-width cell).
 * @param shorthand abbreviated chip handle; may be {@code null}.
 * @param kind discriminator string ({@code SQUADRON} or {@code SPECIAL_COMMAND}), kept as a plain
 *     string so the frontend needs no parallel enum that could drift from the backend.
 */
public record OrgUnitReferenceDto(UUID id, String name, String shorthand, String kind) {}
