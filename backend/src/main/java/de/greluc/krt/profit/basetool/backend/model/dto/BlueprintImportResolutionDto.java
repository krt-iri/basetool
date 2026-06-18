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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * One resolved row of an SCMDB import apply request (#327, Phase 4): the user's decision for a
 * single external name. A blank / {@code null} {@link #productKey} means "skip this name". When the
 * chosen product does not already match the external name by normalization, the apply step learns a
 * {@code blueprint_external_alias} so future imports auto-resolve it.
 *
 * @param externalName the export {@code productName} this decision applies to (from the preview)
 * @param productKey normalized key of the chosen product, or blank / {@code null} to skip
 * @param acquiredAt optional acquisition time to stamp (typically the preview's suggestion)
 * @param note optional free-form note (max 2000 chars)
 */
public record BlueprintImportResolutionDto(
    @NotBlank String externalName,
    String productKey,
    Instant acquiredAt,
    @Size(max = 2000) String note) {}
