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

package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Frontend mirror of the backend {@code JobOrderRequiredBlueprintDto}: one required product of an
 * item order with the count of responsible-org-unit members owning its blueprint. An {@code
 * ownerCount} of zero marks a coverage gap the template highlights.
 *
 * @param productKey the normalized product key the order's item line resolves to
 * @param productName the display name of the required item
 * @param ownerCount the number of responsible-org-unit members owning the blueprint for this
 *     product
 */
public record JobOrderRequiredBlueprintDto(String productKey, String productName, int ownerCount) {}
