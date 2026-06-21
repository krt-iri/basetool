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

package de.greluc.krt.profit.basetool.frontend.model.dto;

import de.greluc.krt.profit.basetool.frontend.model.PayoutPreference;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Data transfer record carrying Mission Participant payload. The {@code orgUnits} list carries the
 * participant's affiliations (zero, one, or several org units — a Staffel and/or Spezialkommandos),
 * mirroring the backend DTO so the roster renders an org-unit badge per affiliation.
 *
 * <p>{@code guestEditToken} mirrors the backend field (security audit M1 / REQ-SEC-018): it is
 * non-{@code null} only on the create response of an anonymous guest sign-up — the per-row
 * capability token the browser stores and replays (as {@code X-Guest-Edit-Token}) to later
 * edit/withdraw that guest row. It is {@code null} on every read/edit response.
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
    String guestEditToken) {
  public String getStartTimeFormatted() {
    return formatInstant(startTime);
  }

  public String getEndTimeFormatted() {
    return formatInstant(endTime);
  }

  private String formatInstant(Instant instant) {
    if (instant == null) {
      return "";
    }
    return instant.atZone(ZoneId.of("Europe/Berlin")).toLocalDateTime().toString();
  }
}
