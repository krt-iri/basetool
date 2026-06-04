/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import java.util.UUID;

/**
 * Picker-optimised wire shape for an org-unit option fed by a user's membership row. Carries only
 * the four fields a {@code <select>} dropdown actually needs ({@link #orgUnitId} as the option
 * value, {@link #orgUnitName} as the visible label, {@link #orgUnitShorthand} as the abbreviated
 * badge text, {@link #kind} so the client can split the options across a "Staffel" and a
 * "Spezialkommandos" {@code <optgroup>}).
 *
 * <p>Separate from {@link OrgUnitMembershipDto} on purpose: the admin SK-roster page already uses
 * the full membership DTO (with the role flags, the {@code joined_at} timestamp and the
 * optimistic-lock version), and forcing every roster row through an extra org-unit JOIN to populate
 * the picker fields would be wasted work. The picker endpoint at {@code GET
 * /api/v1/users/{userId}/memberships} returns this lean shape instead, leaving the heavier roster
 * payload untouched.
 *
 * <p>Even though the wire field is named {@code orgUnitId}, the value points at either a {@link
 * de.greluc.krt.iri.basetool.backend.model.Squadron} or a {@link
 * de.greluc.krt.iri.basetool.backend.model.SpecialCommand} row — both live in the shared {@code
 * org_unit} table since R2.b. Callers branch on {@link #kind} when they need to distinguish the two
 * kinds (e.g. the owner-picker fragment uses two {@code <optgroup>} headers).
 *
 * @param orgUnitId Identifier of the org unit (used as the {@code <option value="...">}).
 * @param orgUnitName Visible name of the org unit (used as the {@code <option>} label).
 * @param orgUnitShorthand Abbreviated shorthand of the org unit ({@code null} when the org unit has
 *     no shorthand set — e.g. legacy Staffel rows from before the shorthand column existed).
 * @param kind Discriminator of the referenced org unit; lets the picker render two {@code
 *     <optgroup>} sections when the user has both kinds, and collapse to a flat list when only one
 *     kind is present.
 * @param isProfitEligible Whether this org unit may be selected as the <em>responsible</em>
 *     (processing) unit of a Job Order. Lets a single {@code /api/v1/org-units/active} fetch feed
 *     both pickers on the create form: the requesting picker offers every option, the responsible
 *     picker keeps only the {@code true} ones — so the (anonymous-reachable) form no longer needs a
 *     second, authenticated call to the Spezialkommando catalog just to learn the SK profit flags.
 */
public record OrgUnitMembershipOptionDto(
    UUID orgUnitId,
    String orgUnitName,
    String orgUnitShorthand,
    OrgUnitKind kind,
    Boolean isProfitEligible) {}
