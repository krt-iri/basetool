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

import de.greluc.krt.profit.basetool.backend.model.PayoutPreference;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data transfer record carrying Mission Participant payload. The {@code orgUnits} list carries the
 * participant's affiliations (zero, one, or several org units — a Staffel and/or Spezialkommandos),
 * replacing the former single {@code squadron} field so a member of both a Staffel and an SK has
 * both badges rendered on the roster.
 *
 * <p>{@code guestEditToken} is non-{@code null} ONLY on the create response of an anonymous guest
 * sign-up (security audit M1 / REQ-SEC-018): the per-row capability token the caller must keep and
 * present (as the {@code X-Guest-Edit-Token} header) to later edit/withdraw that guest row without
 * a login. It is {@code null} on every read/edit response — only the token's hash is persisted, so
 * it cannot be surfaced again after creation.
 */
public record MissionParticipantDto(
    UUID id,
    UserDto user,
    String guestName,
    List<OrgUnitReferenceDto> orgUnits,
    JobTypeDto desiredMissionJobType,
    JobTypeDto plannedMissionJobType,
    String comment,
    Instant startTime,
    Instant endTime,
    PayoutPreference payoutPreference,
    Long version,
    String guestEditToken) {}
