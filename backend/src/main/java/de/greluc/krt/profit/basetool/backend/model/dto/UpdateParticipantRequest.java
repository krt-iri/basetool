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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Inbound request payload for the Update Participant operation. {@code @Size} caps mirror the
 * create payload (audit finding H-2) so an anonymous guest editing their own entry cannot grow the
 * row past the create-time limit.
 *
 * <p>{@code orgUnitIds} replaces the entry's full org-unit affiliation set on update — for a guest
 * it is the caller-submitted, authorization-filtered selection; for a registered participant the
 * submitted list is honoured the same way so the edit modal can adjust affiliations explicitly.
 */
public record UpdateParticipantRequest(
    UUID desiredMissionJobTypeId,
    UUID plannedMissionJobTypeId,
    @Size(max = 1000) String comment,
    @Size(max = 100) String guestName,
    Instant startTime,
    Instant endTime,
    List<UUID> orgUnitIds,
    PayoutPreference payoutPreference,
    @NotNull Long version) {}
