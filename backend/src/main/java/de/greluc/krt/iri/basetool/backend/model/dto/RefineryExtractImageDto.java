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

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Provenance descriptor for one source screenshot of an extracted order ({@code sourceImages[]} in
 * the frozen contract, plan §5). The backend never sees the image itself, only this metadata the
 * desktop extractor recorded; {@code capturedAt} is the one field the import consumes beyond
 * display — the latest capture of an order becomes the draft's {@code startedAt}
 * (REQ-REFINERY-017).
 *
 * @param name screenshot file name on the user's machine, e.g. {@code "frame_213823.png"}
 * @param width capture width in pixels (native, before client-side normalization)
 * @param height capture height in pixels
 * @param cropMode how the work-order panel was isolated: {@code vlm} (model-located), {@code
 *     manual} (user-drawn crop) or {@code precropped} (input already was a panel-only image)
 * @param capturedAt UTC capture instant the extractor derived from the screenshot file (name
 *     timestamp, else file modified time); optional additive v1 field (ADR-0008), {@code null} when
 *     the producer is older or the capture time was undeterminable
 */
public record RefineryExtractImageDto(
    @NotNull @Size(max = 255) String name,
    @Positive Integer width,
    @Positive Integer height,
    @Size(max = 32) String cropMode,
    Instant capturedAt) {}
