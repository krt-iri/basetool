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

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * One delivered line of an item-handover create payload: how many whole units of a specific ordered
 * item line ({@code jobOrderItemId}) changed hands. The amount must not exceed the line's
 * outstanding (ordered minus already-delivered) quantity, enforced server-side.
 *
 * @param jobOrderItemId the ordered item line being fulfilled
 * @param amount whole-unit count delivered for that line (≥ 1)
 */
public record JobOrderItemHandoverEntryCreateDto(
    @NotNull UUID jobOrderItemId, @NotNull @Min(1) Integer amount) {}
