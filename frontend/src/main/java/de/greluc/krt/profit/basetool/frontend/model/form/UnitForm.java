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

package de.greluc.krt.profit.basetool.frontend.model.form;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Form-binding object for Unit input. {@code name} is the optional display name (the backend
 * derives the stored name from ship / ship type when blank); {@code responsibleUserId} optionally
 * pins an explicit responsible person; {@code note} is a free-text planning note.
 */
public record UnitForm(
    @Size(max = 255) String name,
    UUID shipTypeId,
    UUID shipId,
    Boolean highValueUnit,
    Double frequency,
    UUID responsibleUserId,
    @Size(max = 500) String note) {}
