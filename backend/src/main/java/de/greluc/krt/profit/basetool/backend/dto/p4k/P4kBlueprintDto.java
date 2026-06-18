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

package de.greluc.krt.profit.basetool.backend.dto.p4k;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One crafting-blueprint record from the P4K catalog ({@code CraftingBlueprintRecord} under {@code
 * crafting/blueprints}). Reconciled against the local {@code blueprint} table by {@link #guid} (=
 * {@code scwiki_uuid}) first, then {@link #key} (= {@code scwiki_key}, a {@code BP_CRAFT_*} token).
 *
 * <p>The importer enriches blueprint scalars (fill-if-null) and then resolves <em>existing</em>
 * unresolved {@code blueprint_ingredient} rows by their stored Wiki UUIDs — it does not create
 * blueprints or rewrite ingredient rows from {@link #ingredients}. The {@code slots} array in the
 * source JSON is unconsumed and dropped by {@code @JsonIgnoreProperties}.
 *
 * <p>Any field may be {@code null} when the source DCB did not resolve it.
 *
 * @param guid DataForge {@code __ref} GUID (string form of {@code blueprint.scwiki_uuid})
 * @param key blueprint key {@code BP_CRAFT_*}; matches {@code blueprint.scwiki_key}
 * @param path source DCB record path (forensic; not persisted)
 * @param categoryGuid blueprint category GUID (forensic; not persisted)
 * @param producedItemGuid produced item {@code __ref} GUID, resolved against {@code
 *     game_item.external_uuid} and enriched into {@code blueprint.output_item} when currently null
 * @param craftTimeSeconds craft time in seconds, enriched into {@code blueprint.craft_time_seconds}
 *     when currently null
 * @param ingredients ingredient lines (carried for completeness; the resolve step reads the
 *     persisted ingredient UUIDs, not this list)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record P4kBlueprintDto(
    String guid,
    String key,
    String path,
    String categoryGuid,
    String producedItemGuid,
    Integer craftTimeSeconds,
    List<P4kIngredientDto> ingredients) {}
