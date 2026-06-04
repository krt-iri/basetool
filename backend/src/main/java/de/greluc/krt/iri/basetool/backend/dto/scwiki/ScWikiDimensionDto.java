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

package de.greluc.krt.iri.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Bounding-box dimensions ({@code {x,y,z}}) nested inside a {@link ScWikiItemDto}
 * (SC_WIKI_SYNC_PLAN.md §3.3). Any axis may be {@code null} when the Wiki omits it.
 *
 * @param x width in metres
 * @param y height in metres
 * @param z depth / length in metres
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiDimensionDto(Double x, Double y, Double z) {}
