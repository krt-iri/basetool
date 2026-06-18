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

/**
 * Frontend mirror of the backend {@code SyncReportPurgeResultDto}. Lets {@code
 * AdminSyncReportsPageController} deserialise the response of the "delete reports older than X
 * days" action without depending on the backend module.
 *
 * <p>Fields and types must stay in lockstep with {@code
 * de.greluc.krt.profit.basetool.backend.model.dto.SyncReportPurgeResultDto} — any backend change
 * requires a matching change here in the same commit (mirror-DTO rule).
 *
 * @param deleted number of sync-report rows deleted by the purge
 */
public record SyncReportPurgeResultDto(int deleted) {}
