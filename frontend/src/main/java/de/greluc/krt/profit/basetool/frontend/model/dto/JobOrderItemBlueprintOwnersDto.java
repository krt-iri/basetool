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

package de.greluc.krt.profit.basetool.frontend.model.dto;

import java.util.List;

/**
 * Frontend mirror of the backend {@code JobOrderItemBlueprintOwnersDto}: the item job-order
 * blueprint-coverage view fetched for the order detail page. Loaded only for {@code ITEM} orders
 * and only when the viewer is a member of the responsible org unit (the backend returns 403
 * otherwise, which the page controller swallows so the section is simply omitted).
 *
 * @param requiredBlueprints the order's distinct required products with per-product owner counts
 * @param owners the members owning at least one required blueprint, with the products they hold
 */
public record JobOrderItemBlueprintOwnersDto(
    List<JobOrderRequiredBlueprintDto> requiredBlueprints,
    List<JobOrderBlueprintOwnerDto> owners) {}
