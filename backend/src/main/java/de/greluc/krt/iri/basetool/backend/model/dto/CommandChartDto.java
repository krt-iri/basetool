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

import java.util.List;
import java.util.UUID;

/**
 * One Kommando(gruppe) within a Staffel: the named command itself, its optional Kommandoleiter, its
 * optional Stv. Kommandoleiter, and the Ensigns reporting into it. Up to four of these hang under a
 * {@link SquadronChartDto}.
 *
 * <p>The Kommando is its own row (a {@link
 * de.greluc.krt.iri.basetool.backend.model.OrgChartPositionType#COMMAND_LEAD}); {@link #positionId}
 * / {@link #version} are that row's handle so the inline editor can rename it, remove it (cascading
 * its Stv. + Ensigns), or assign / reassign its Kommandoleiter. The leader lives on that same row,
 * so it is projected inline as {@link #leaderUserId} / {@link #leaderUserName} rather than as a
 * separate node — both {@code null} while the Kommandoleiter seat is vacant.
 *
 * @param positionId id of the underlying Kommando row; the handle for rename / remove / assign-lead
 *     / add-child actions.
 * @param name the Kommando's display name, or {@code null} when it is unnamed (the UI shows a
 *     fallback label).
 * @param version optimistic-lock version of the Kommando row, echoed back on rename / assign-lead.
 * @param sortIndex stable display order among the Staffel's Kommandos.
 * @param leaderUserId id of the Kommandoleiter, or {@code null} when the seat is vacant.
 * @param leaderUserName the Kommandoleiter's effective display name, or {@code null} when vacant.
 * @param deputy the Stv. Kommandoleiter node, or {@code null} when no deputy is assigned.
 * @param ensigns the Ensigns reporting into this Kommando, ordered for display; never {@code null},
 *     possibly empty.
 */
public record CommandChartDto(
    UUID positionId,
    String name,
    Long version,
    int sortIndex,
    UUID leaderUserId,
    String leaderUserName,
    OrgChartNodeDto deputy,
    List<OrgChartNodeDto> ensigns) {}
