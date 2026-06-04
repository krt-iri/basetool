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

package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound JSON record for UEX Corp's {@code /categories} endpoint. Mapped to the project's own
 * {@code UexCategory} reference entity by {@code UexCategoryRefService}; downstream code consumes
 * the entity, not this DTO.
 *
 * <p>{@code type} is one of {@code "item"} or {@code "vehicle"} — used by {@code
 * UexItemSyncService} to know whether an entry under this category is bound for the {@code
 * game_item} table (items) or the {@code ship_type} table (vehicles).
 *
 * @param id UEX integer category id (1..98+); stable across runs
 * @param type {@code "item"} or {@code "vehicle"}
 * @param section coarse grouping, e.g. {@code "Armor"}, {@code "Vehicle Weapons"}, {@code
 *     "Systems"}
 * @param name subcategory display name, e.g. {@code "Helmets"}, {@code "Torso"}
 * @param isGameRelated UEX integer flag (0/1); driving the inner-loop filter
 * @param isMining UEX integer flag (0/1)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UexCategoryDto(
    @JsonProperty("id") Integer id,
    @JsonProperty("type") String type,
    @JsonProperty("section") String section,
    @JsonProperty("name") String name,
    @JsonProperty("is_game_related") Integer isGameRelated,
    @JsonProperty("is_mining") Integer isMining) {}
