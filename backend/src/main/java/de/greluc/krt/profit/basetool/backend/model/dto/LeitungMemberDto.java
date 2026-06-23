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

package de.greluc.krt.profit.basetool.backend.model.dto;

import de.greluc.krt.profit.basetool.backend.model.MembershipRole;
import java.util.UUID;

/**
 * One roster row in the delegated Leitung view (epic #800, REQ-ROLE-004/006): a member of a managed
 * org unit, carrying the functional rank and (for in-Kommando squadron ranks) the bound
 * Kommandogruppe so the UI can render the current state and pre-fill the appointment forms. The
 * {@code version} is the membership row's optimistic-lock counter, echoed back on a squadron-rank
 * write so concurrent edits surface as 409.
 *
 * @param userId the member's account id; always populated.
 * @param userDisplayName the member's display name (display name with username fallback) for the
 *     roster label; never an empty cell.
 * @param role the member's functional rank in this org unit; never {@code null} ({@code MEMBER} for
 *     a plain member).
 * @param kommandoGroupId the bound Kommandogruppe for a Kommandoleiter / stellv. Kommandoleiter /
 *     Ensign, or {@code null} for every other rank (and a Staffelleiter-direct Ensign).
 * @param version the membership row's optimistic-lock version, required on a squadron-rank write.
 */
public record LeitungMemberDto(
    UUID userId, String userDisplayName, MembershipRole role, UUID kommandoGroupId, long version) {}
