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

package de.greluc.krt.iri.basetool.backend.dto.p4k;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Root of the single JSON catalog the external "KRT P4K Reader" tool produces from the game's
 * {@code Data/Game2.dcb} (DataForge). An admin uploads this object and {@code P4kImportService}
 * enriches the matching {@code game_item} / {@code ship_type} / {@code manufacturer} / {@code
 * material} / {@code blueprint} rows by the DataForge {@code __ref} GUID — it never mass-seeds
 * rows.
 *
 * <p>Field names are camelCase and map 1:1 to the JSON keys, so no {@code @JsonProperty} is needed.
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} ignores the {@code meta} block (and any
 * forward-compatible additions) the importer does not consume. Any array may be {@code null} or
 * absent; the service treats a missing array as empty.
 *
 * @param manufacturers manufacturer records (joined on {@code scwiki_uuid} / {@code abbreviation})
 * @param items item records (joined on {@code game_item.external_uuid} / {@code class_name})
 * @param ships ship records (joined on {@code ship_type.external_uuid} / {@code class_name})
 * @param commodities commodity records (joined on {@code material.scwiki_uuid} / {@code name})
 * @param blueprints crafting-blueprint records (joined on {@code blueprint.scwiki_uuid} / {@code
 *     scwiki_key})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record P4kCatalogDto(
    List<P4kManufacturerDto> manufacturers,
    List<P4kItemDto> items,
    List<P4kShipDto> ships,
    List<P4kCommodityDto> commodities,
    List<P4kBlueprintDto> blueprints) {}
