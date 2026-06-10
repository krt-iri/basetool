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

import java.util.List;

/**
 * Best-effort, <b>never persisted</b> pre-fill produced by {@code POST
 * /api/v1/refinery-orders/import-extract} from a {@code RefineryExtract} JSON (#434, plan §7.5).
 * The frontend pours {@link #order} into the existing refinery create form and renders {@link
 * #issues} as inline review flags; saving still goes through the untouched {@code POST
 * /api/v1/refinery-orders} create path with full validation.
 *
 * <p>The embedded order intentionally violates save-time constraints where matching failed:
 * unmatched rows carry a {@code null} {@code inputMaterial}, an unresolved location stays {@code
 * null} despite being {@code @NotNull} on create. That is the point — the review form forces the
 * user to complete exactly those fields.
 *
 * @param order the pre-filled order; goods appear in on-screen ({@code rowIndex}) order, duplicate
 *     material rows preserved
 * @param issues every finding the user must see, ordered as encountered (order-level first, then
 *     per-row); empty when everything matched cleanly
 * @param goodsMatched number of draft rows whose input material was resolved
 * @param goodsTotal number of material rows read from the screenshots (incl. skipped ones)
 * @param rowsSkipped number of rows not added to the draft (refine-off, zero-quantity, un-quoted)
 */
public record RefineryImportDraftDto(
    RefineryOrderDto order,
    List<ImportIssueDto> issues,
    int goodsMatched,
    int goodsTotal,
    int rowsSkipped) {}
