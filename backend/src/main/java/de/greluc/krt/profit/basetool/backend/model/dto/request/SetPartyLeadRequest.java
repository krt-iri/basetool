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

package de.greluc.krt.profit.basetool.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request DTO for assigning (or clearing) a mission's party lead (Partyleiter) via {@code PUT
 * /api/v1/missions/{id}/party-lead}.
 *
 * <p>Mirrors the participant-add input contract: callers either submit an explicit {@code userId}
 * (picked from the user autocomplete) or a free-text {@code guestName}. A non-blank {@code
 * guestName} that has no {@code userId} is resolved case-insensitively against registered members
 * in the controller — a unique match is linked as a registered party lead, no match is stored as a
 * guest handle, multiple matches return 409. Submitting neither clears the party lead.
 *
 * <p>The {@code version} must match the mission's current {@code partyLeadVersion} (a
 * section-scoped counter, NOT the global {@code Mission.version}) so concurrent party-lead edits
 * surface as a 409 instead of silently overwriting each other. The {@code @Size} cap on {@code
 * guestName} matches {@code Mission.partyLeadGuestName}'s column length.
 *
 * @param userId explicit registered-user reference (from the autocomplete pick), or {@code null}
 * @param guestName free-text party-lead handle, or {@code null}; capped at 100 characters
 * @param version expected {@code partyLeadVersion} of the mission for optimistic-lock validation
 */
public record SetPartyLeadRequest(
    UUID userId, @Size(max = 100) String guestName, @NotNull Long version) {}
