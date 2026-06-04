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

import de.greluc.krt.iri.basetool.backend.model.QualityRequirement;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Per-material quality choice the requester makes for one ordered item line. The server re-derives
 * the required quantity from the blueprint (authoritative) and applies this {@code quality} to the
 * matching derived material; a material the client omits falls back to the blueprint-derived
 * default (GOOD when the ingredient's {@code minQuality} is 700+, else NONE).
 *
 * @param materialId the material this choice applies to
 * @param quality the requested quality floor ({@code GOOD} = 700+, {@code NONE} = no floor)
 */
public record CreateJobOrderItemMaterialDto(
    @NotNull UUID materialId, @NotNull QualityRequirement quality) {}
