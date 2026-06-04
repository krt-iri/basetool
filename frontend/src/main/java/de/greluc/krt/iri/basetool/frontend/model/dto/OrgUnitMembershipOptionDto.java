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
 * @param isProfitEligible Whether the org unit may process Job Orders (be the responsible unit).
 *     {@code true} for the entries the Job Order create form keeps in its responsible picker; the
 *     requesting picker offers every entry regardless. {@code null} on the {@code
 *     /users/{id}/memberships} payload paths that predate the flag — treat null as "not eligible".
 */
public record OrgUnitMembershipOptionDto(
    UUID orgUnitId,
    String orgUnitName,
    String orgUnitShorthand,
    String kind,
    Boolean isProfitEligible) {}
