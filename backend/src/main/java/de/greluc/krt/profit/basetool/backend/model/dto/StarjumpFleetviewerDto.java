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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Internal DTO for the root of a StarJump FleetViewer JSON export (the "Hangar Link" download from
 * {@code https://fleetviewer.link}). Unlike the two array-based formats (CCU Game Fleetview and
 * HangarXPLOR Shiplist), a FleetViewer export is a single JSON <em>object</em> carrying a {@code
 * type} discriminator, a {@code version} and a {@code canvasItems} array that holds the ship tiles
 * alongside decorative widgets.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} discards the many top-level layout fields
 * (background, zoom, pan, render box, icon positions …) the import flow does not need.
 *
 * <p>Format example (top-level shape, canvas items elided):
 *
 * <pre>
 * {
 *   "type":        "starjumpFleetviewer",
 *   "version":     1,
 *   "canvasItems": [ { "itemType": "SHIP", "shipSlug": "perseus", "defaultText": "Perseus" }, ... ]
 * }
 * </pre>
 *
 * @param type the export discriminator (expected {@code "starjumpFleetviewer"}, case-insensitive)
 * @param canvasItems the canvas elements; ship tiles and decorative widgets intermixed, may be
 *     {@code null} on a malformed/empty export
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StarjumpFleetviewerDto(
    @JsonProperty("type") String type,
    @JsonProperty("canvasItems") List<StarjumpCanvasItemDto> canvasItems) {}
