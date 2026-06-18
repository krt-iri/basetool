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

import org.jetbrains.annotations.Nullable;

/**
 * Frontend mirror of the backend {@code JobOrderAssigneeDto}: one job-order assignee, carrying the
 * assigned {@link UserDto}, the assignee's optional free-text {@code note} and the assignee edge's
 * own optimistic-lock {@code version}. The version is echoed back by the note PUT/DELETE so a stale
 * edit surfaces as HTTP 409.
 *
 * @param user the assigned user
 * @param note the assignee's note, or {@code null} when none is set
 * @param version optimistic-lock version of the assignee edge
 */
public record JobOrderAssigneeDto(UserDto user, @Nullable String note, Long version) {}
