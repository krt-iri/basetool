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
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body to assign (or change) a squadron leadership rank on an existing Staffel member (epic
 * #800, REQ-ROLE-003/004). The squadron and the target user are taken from the path ({@code
 * /api/v1/squadrons/{squadronId}/ranks/{userId}}). The user must already be a member of that
 * Staffel; the rank must be a squadron rank ({@link MembershipRole#isSquadronRank()}) — any other
 * value is rejected with a clean 400.
 *
 * <p>The {@code kommandoGroupId} pairing follows the V185 {@code
 * chk_org_unit_membership_kommando_group_role} CHECK: {@link MembershipRole#KOMMANDOLEITER} /
 * {@link MembershipRole#STELLV_KOMMANDOLEITER} MUST reference a group of the same squadron; {@link
 * MembershipRole#ENSIGN} MAY ({@code null} = "allgemein der Staffelleitung"); {@link
 * MembershipRole#STAFFELLEITER} MUST be {@code null}.
 *
 * @param role the squadron rank to set; required, must be a squadron rank.
 * @param kommandoGroupId the Kommandogruppe to bind, or {@code null}; constrained per the rank.
 * @param version the optimistic-lock version of the member's row the client last read; required.
 */
public record AssignSquadronRankRequest(
    @NotNull MembershipRole role, UUID kommandoGroupId, @NotNull Long version) {}
