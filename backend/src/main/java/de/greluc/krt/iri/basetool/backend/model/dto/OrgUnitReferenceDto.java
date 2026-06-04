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
 * Narrow reference projection of an {@code OrgUnit} (a {@code Squadron} or a {@code
 * SpecialCommand}) carrying only what the UI needs to render an org-unit badge: the surrogate id,
 * the long-form name for tooltips, the short handle for the chip text, and the {@link #kind}
 * discriminator so the client can style Staffel and Spezialkommando chips differently.
 *
 * <p>Embedded as a list into {@link MissionParticipantDto}: a single participant may be affiliated
 * with zero, one, or several org units (a member of a Staffel plus one or more Spezialkommandos),
 * so the participant snapshot exposes a {@code List<OrgUnitReferenceDto>} rather than the single
 * {@code SquadronDto} the pre-org-unit model used. Deliberately omits {@code active} / {@code
 * description} / {@code version}, which belong on the full admin DTOs.
 *
 * @param id surrogate id of the org unit.
 * @param name long-form display name (e.g. for tooltips / full-width cells).
 * @param shorthand abbreviated three- to five-letter handle rendered on the chip; may be {@code
 *     null} for legacy rows without a shorthand.
 * @param kind discriminator distinguishing a Staffel ({@link OrgUnitKind#SQUADRON}) from a
 *     Spezialkommando ({@link OrgUnitKind#SPECIAL_COMMAND}).
 */
public record OrgUnitReferenceDto(UUID id, String name, String shorthand, OrgUnitKind kind) {}
