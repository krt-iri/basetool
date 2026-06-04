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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Internal DTO for parsing a single entry from a Fleetyards hangar JSON export (see {@code
 * https://fleetyards.net}). A Fleetyards export is a flat array of vehicle records carrying the RSI
 * ship code, manufacturer codes, a kebab-case slug and a handful of per-ship flags (wanted,
 * flagship, public, nameVisible, saleNotify) plus {@code groups}/{@code modules}/{@code upgrades}
 * arrays. The import flow consumes only the three fields declared below; everything else is layout
 * or store metadata it does not need.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} keeps Jackson tolerant towards the many
 * unconsumed fields and any forward-compatible additions in a future Fleetyards release so a new
 * field does not break our parse step.
 *
 * <p>Format example (single entry):
 *
 * <pre>
 * {
 *   "name":             "A1 Spirit",
 *   "slug":             "crus-a1-spirit",
 *   "shipCode":         "crus_spirit_a1",
 *   "manufacturerName": "Crusader Industries",
 *   "manufacturerCode": "CRUS",
 *   "shipName":         "Koto",
 *   "wanted":           false,
 *   "flagship":         false,
 *   "public":           true,
 *   "nameVisible":      true,
 *   "saleNotify":       false,
 *   "groups":           [],
 *   "modules":          [],
 *   "upgrades":         []
 * }
 * </pre>
 *
 * @param name the ship-model name (e.g. {@code "A1 Spirit"}), the primary key fed into the name
 *     matcher
 * @param shipName the user's custom ship name (e.g. {@code "Koto"}), or {@code null}/blank when the
 *     ship was never individually named; carried as the imported ship's individual name when it is
 *     not merely an echo of {@code name}
 * @param slug Fleetyards' manufacturer-prefixed kebab-case slug (e.g. {@code "crus-a1-spirit"}),
 *     used as the slug-fallback match key against {@code ShipType.uexSlug} / {@code
 *     ShipType.scwikiSlug} when the name stages miss
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FleetyardsEntryDto(
    @JsonProperty("name") String name,
    @JsonProperty("shipName") String shipName,
    @JsonProperty("slug") String slug) {}
