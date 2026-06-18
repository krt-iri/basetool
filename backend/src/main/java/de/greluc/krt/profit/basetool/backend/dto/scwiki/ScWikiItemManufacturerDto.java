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

package de.greluc.krt.profit.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Manufacturer reference ({@code {uuid,name,code}}) nested inside a {@link ScWikiItemDto}
 * (SC_WIKI_SYNC_PLAN.md §3.3). R4 captures it but does NOT write it onto {@code game_item} — the
 * manufacturer link is sticky on the UEX value (§6.3.5); the dedicated manufacturer reconciliation
 * is R6. The DTO exists so the payload binds cleanly and R6 has the data ready.
 *
 * @param uuid Wiki manufacturer UUID
 * @param name manufacturer display name
 * @param code manufacturer short code (e.g. {@code "RSI"})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiItemManufacturerDto(UUID uuid, String name, String code) {}
