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

package de.greluc.krt.iri.basetool.ingest.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Provenance for one source image stitched into a {@link RefineryExtractOrderDto}. Image bytes
 * never leave the user's machine (ADR-0007); only this metadata travels. Mirror of the backend
 * record.
 *
 * @param name the source file name (provenance / capture-time derivation)
 * @param width the image width in pixels
 * @param height the image height in pixels
 * @param cropMode how the panel was cropped before extraction
 * @param capturedAt the capture instant, when derivable; drives the order start time backend-side
 */
public record RefineryExtractImageDto(
    @NotNull @Size(max = 255) String name,
    @Positive Integer width,
    @Positive Integer height,
    @Size(max = 32) String cropMode,
    Instant capturedAt) {}
