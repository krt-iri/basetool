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

import java.util.List;

/**
 * Per-material roll-up of the Lager for the {@code /grouped} list views. Each material carries its
 * squadron-wide totals ({@code totalAmount}, amount-weighted {@code averageQuality}, {@code
 * maxQuality}) and the list of {@link InventoryStackDto} stacks it breaks down into — one stack per
 * distinct stock identity (owner, location, quality, association, owner pool). The stacks in turn
 * hold the individual append-only entries, so the UI renders Material → Stack → Entries.
 *
 * @param material the grouping material
 * @param totalAmount the summed quantity across every stack of this material
 * @param averageQuality the amount-weighted mean quality across every stack
 * @param maxQuality the highest quality value seen across the stacks
 * @param stacks the per-stock-identity stacks this material breaks down into
 */
public record GroupedInventoryDto(
    MaterialReferenceDto material,
    Double totalAmount,
    Double averageQuality,
    Integer maxQuality,
    List<InventoryStackDto> stacks) {}
