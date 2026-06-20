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

import java.util.List;
import java.util.UUID;

/**
 * Frontend mirror of one Kommando(gruppe) within a Staffel. The Kommando is its own row, so {@link
 * #positionId} / {@link #version} are the handle the inline editor uses to rename it, remove it, or
 * assign / reassign its Kommandoleiter. The Kommandoleiter lives on that row and is carried inline
 * ({@link #leaderUserId} / {@link #leaderUserName} for an account, {@link #leaderDisplayName} for a
 * free-text name, all {@code null} while the seat is vacant).
 *
 * @param positionId id of the Kommando row; handle for rename / remove / assign-lead / add-child.
 * @param name the Kommando's display name, or {@code null} when unnamed (template shows a
 *     fallback).
 * @param version optimistic-lock version of the Kommando row, echoed back on rename / assign-lead.
 * @param sortIndex stable display order among the Staffel's Kommandos.
 * @param leaderUserId id of the Kommandoleiter account, or {@code null} for a free-text leader or a
 *     vacant seat.
 * @param leaderUserName the Kommandoleiter account's effective display name, or {@code null} for a
 *     free-text leader or a vacant seat.
 * @param leaderDisplayName the free-text Kommandoleiter name for a member without a Basetool
 *     account, or {@code null} when the leader is an account or the seat is vacant; mutually
 *     exclusive with {@code leaderUserId}.
 * @param deputy the Stv. Kommandoleiter node, or {@code null} when vacant.
 * @param ensigns the Ensigns reporting into this Kommando; never {@code null}, possibly empty.
 */
public record CommandChartDto(
    UUID positionId,
    String name,
    Long version,
    int sortIndex,
    UUID leaderUserId,
    String leaderUserName,
    String leaderDisplayName,
    OrgChartNodeDto deputy,
    List<OrgChartNodeDto> ensigns) {}
