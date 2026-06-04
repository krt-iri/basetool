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
 * Data transfer record carrying Refinery Good payload.
 *
 * <p>{@code yieldBonusPercent} is a UEX-derived, read-only enrichment: it carries the percentage
 * bonus or malus the chosen refinery applies to {@code inputMaterial} (positive = bonus, negative =
 * malus, {@code null} = no yield row known for this refinery/material pair, e.g. when the order has
 * no location resolvable to a UEX terminal yet). The backend populates the field on read; inbound
 * write payloads ignore it and the database persists nothing for it — see {@code
 * RefineryOrderMapper.toDto(RefineryOrder, Map)} and {@code
 * RefineryOrderService.getYieldBonusByMaterialForLocation(Location)}.
 */
public record RefineryGoodDto(
    UUID id,
    @NotNull MaterialDto inputMaterial,
    @NotNull @Min(1) Integer inputQuantity,
    MaterialDto outputMaterial,
    @NotNull @Min(1) Integer outputQuantity,
    Integer quality,
    Integer yieldBonusPercent) {}
