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

package de.greluc.krt.profit.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound JSON record for UEX Corp's {@code /items} endpoint (R2). Mapped to the project's own
 * {@code GameItem} entity by {@code UexItemSyncService}; downstream code consumes the entity, not
 * this DTO.
 *
 * <p>The {@code uuid} field is captured as a raw {@link String} because UEX returns an empty string
 * ({@code ""}) for the ~30% of rows that have no in-game asset UUID (Avionics 100%, Decorations
 * 88%, Liveries 42%, Armor ~33% — see SC_WIKI_SYNC_PLAN.md §3.6). Jackson cannot bind the empty
 * string to {@link java.util.UUID}; the sync service parses the non-empty values with {@link
 * java.util.UUID#fromString(String)} and treats empty / blank as {@code null}.
 *
 * @param id UEX integer item id (stable across runs); the secondary cross-source join key
 * @param idParent variant grouping (0 = no parent); unused in R2 but captured for forensics
 * @param idCategory FK to {@code /categories[].id} — the kind-derivation lookup key
 * @param idCompany FK to {@code /companies[].id} — resolved to {@code manufacturer} entity
 * @param idVehicle FK to {@code /vehicles[].id} — set for vehicle-bound items (paints, components)
 * @param name display name
 * @param slug kebab-case URL slug; consumed by R3 Wiki slug-fallback resolution
 * @param uuid in-game RSI asset UUID (shared with SC Wiki) — empty string for ~30% of rows
 * @param size weapon / component size tier as a string ({@code "1"}, {@code "2"}, …)
 * @param color primary color of the variant
 * @param color2 secondary color
 * @param quality quality tier (0..n)
 * @param urlStore RSI pledge-store URL
 * @param section denormalised category section (mirrors {@link UexCategoryDto#section})
 * @param category denormalised category name (mirrors {@link UexCategoryDto#name})
 * @param companyName denormalised manufacturer name
 * @param vehicleName denormalised vehicle name when {@code idVehicle != 0}
 * @param screenshot screenshot URL
 * @param isExclusivePledge UEX flag (0/1)
 * @param isExclusiveSubscriber UEX flag (0/1)
 * @param isExclusiveConcierge UEX flag (0/1)
 * @param isCommodity UEX flag (0/1) — set on items that also appear in {@code /commodities}
 * @param isHarvestable UEX flag (0/1)
 * @param gameVersion game version the row was last seen in
 * @param dateAdded unix timestamp
 * @param dateModified unix timestamp
 * @param notification freeform UEX note
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UexItemDto(
    @JsonProperty("id") Integer id,
    @JsonProperty("id_parent") Integer idParent,
    @JsonProperty("id_category") Integer idCategory,
    @JsonProperty("id_company") Integer idCompany,
    @JsonProperty("id_vehicle") Integer idVehicle,
    @JsonProperty("name") String name,
    @JsonProperty("slug") String slug,
    @JsonProperty("uuid") String uuid,
    @JsonProperty("size") String size,
    @JsonProperty("color") String color,
    @JsonProperty("color2") String color2,
    @JsonProperty("quality") Integer quality,
    @JsonProperty("url_store") String urlStore,
    @JsonProperty("section") String section,
    @JsonProperty("category") String category,
    @JsonProperty("company_name") String companyName,
    @JsonProperty("vehicle_name") String vehicleName,
    @JsonProperty("screenshot") String screenshot,
    @JsonProperty("is_exclusive_pledge") Integer isExclusivePledge,
    @JsonProperty("is_exclusive_subscriber") Integer isExclusiveSubscriber,
    @JsonProperty("is_exclusive_concierge") Integer isExclusiveConcierge,
    @JsonProperty("is_commodity") Integer isCommodity,
    @JsonProperty("is_harvestable") Integer isHarvestable,
    @JsonProperty("game_version") String gameVersion,
    @JsonProperty("date_added") Long dateAdded,
    @JsonProperty("date_modified") Long dateModified,
    @JsonProperty("notification") String notification) {}
