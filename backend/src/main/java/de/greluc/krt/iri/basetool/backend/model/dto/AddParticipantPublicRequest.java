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

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Inbound request payload for the Add Participant Public operation. {@code @Size} caps cap the
 * anonymous attack surface — without them an unauthenticated caller could spam multi-megabyte
 * {@code guestName} / {@code comment} payloads through the public participant-add endpoint until
 * the {@code mission_participant} table is full (audit finding H-2).
 *
 * <p>{@code orgUnitIds} is honoured only for GUEST entries (and only when the authenticated caller
 * may label those org units — see {@code MissionService.resolveGuestSubmittedOrgUnits}); for a
 * registered participant the affiliations are auto-derived server-side from the user's memberships
 * and any submitted list is ignored.
 */
public record AddParticipantPublicRequest(
    UUID userId,
    @Size(max = 100) String guestName,
    UUID desiredJobTypeId,
    @Size(max = 1000) String comment,
    List<UUID> orgUnitIds) {}
