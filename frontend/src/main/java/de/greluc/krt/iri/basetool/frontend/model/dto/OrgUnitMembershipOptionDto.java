package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code OrgUnitMembershipOptionDto} wire shape returned by {@code
 * GET /api/v1/users/{id}/memberships}. Drives the R5.d owner-picker fragment: each row becomes a
 * {@code <option>} in the picker dropdown, with {@link #orgUnitId} as the option value and {@link
 * #orgUnitName} as the visible label. {@link #kind} lets the fragment partition the options into
 * "Staffel" and "Spezialkommandos" {@code <optgroup>} headers.
 *
 * @param orgUnitId Identifier of the org unit (used as the {@code <option value="...">}).
 * @param orgUnitName Visible name (used as the option label).
 * @param orgUnitShorthand Abbreviated badge text; may be {@code null}.
 * @param kind Discriminator string ({@code SQUADRON} or {@code SPECIAL_COMMAND}) — kept as a plain
 *     string so the frontend does not need a parallel enum that drifts out of sync with the
 *     backend.
 */
public record OrgUnitMembershipOptionDto(
    UUID orgUnitId, String orgUnitName, String orgUnitShorthand, String kind) {}
