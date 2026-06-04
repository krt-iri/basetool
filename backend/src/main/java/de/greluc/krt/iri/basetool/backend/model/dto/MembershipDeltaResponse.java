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

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Response payload for the SPEZIALKOMMANDO_PLAN.md §7.4 single-POST membership-delta endpoint.
 * Carries the user's post-write membership state so the frontend can re-render the form without a
 * follow-up GET — saves one round-trip and avoids the "what's the new version?" question after each
 * save.
 *
 * @param memberships the user's complete current Staffel + SK membership list, sorted Staffel-
 *     first then SK alphabetical. Never {@code null}; possibly empty when the user has been
 *     stripped of every membership in the same transaction.
 */
public record MembershipDeltaResponse(@NotNull List<OrgUnitMembershipDto> memberships) {}
