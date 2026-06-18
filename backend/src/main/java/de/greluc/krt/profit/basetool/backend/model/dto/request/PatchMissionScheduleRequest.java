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
import java.time.Instant;
import org.jetbrains.annotations.Nullable;

/**
 * Request DTO for a partial update of the schedule section of a mission.
 *
 * <p>All timestamps are accepted and stored in UTC ({@link Instant}). Display in the frontend uses
 * the user's local timezone.
 *
 * <p>The {@code version} field is the dedicated {@code mission.schedule_version} section counter —
 * not the global {@code Mission.@Version}. Concurrent edits on the core or flags section therefore
 * never invalidate a schedule patch in flight (and vice versa).
 */
public record PatchMissionScheduleRequest(
    @Nullable Instant meetingTime,
    @Nullable Instant plannedStartTime,
    @Nullable Instant plannedEndTime,
    @Nullable Instant actualStartTime,
    @Nullable Instant actualEndTime,
    @NotNull Long version) {}
