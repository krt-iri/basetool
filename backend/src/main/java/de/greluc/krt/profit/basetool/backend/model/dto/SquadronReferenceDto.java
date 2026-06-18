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

import java.util.UUID;

/**
 * Narrow reference projection of a {@code Squadron} carrying only the fields the UI needs to render
 * a badge or a list column: the surrogate id, the long-form name (for tooltips / full-width cells),
 * and the short three- or four-letter handle ({@code shorthand}) the corporate design uses on
 * chips. Embedded into the per-aggregate list / detail DTOs (Mission, JobOrder, Inventory,
 * Refinery, Operation, Ship) so the squadron column / badge can be rendered without a separate
 * lookup. Deliberately omits {@code active} / {@code description} / {@code version} - those belong
 * on the full {@link SquadronDto} used by the admin squadron-management page.
 */
public record SquadronReferenceDto(UUID id, String name, String shorthand) {}
