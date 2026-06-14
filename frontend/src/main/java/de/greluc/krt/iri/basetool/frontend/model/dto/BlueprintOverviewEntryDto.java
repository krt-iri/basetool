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
 * Frontend mirror of the backend {@code BlueprintOverviewEntryDto} (#364): one row of the org-unit
 * blueprint availability overview — a distinct variant family (base item with its cosmetic variants
 * collapsed) plus how many in-scope members own any member of it.
 *
 * @param productKey variant family key (the drill-down key)
 * @param productName the family's display label (case-preserving base name)
 * @param ownerCount number of distinct in-scope members that own the base or any variant
 */
public record BlueprintOverviewEntryDto(String productKey, String productName, long ownerCount) {}
