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

package de.greluc.krt.profit.basetool.backend.model.dto;

import java.util.UUID;

/**
 * Flat, internal projection of one job-order-linked inventory row, used to batch the per-(order,
 * material) stock sums of the paged job-order list into a single query (REQ-DATA-003). Instead of
 * firing one {@code SUM(amount)} aggregate per material bucket per order — an O(orders × materials)
 * fan-out on the most-visited orders page — the list path loads every linked row once via {@code
 * InventoryItemRepository.findMaterialStockRowsByJobOrderIds} and sums the buckets in memory,
 * applying each bucket's own quality floor. Carrying the raw {@code quality} (nullable) and {@code
 * amount} lets the caller reproduce the exact {@code (minQuality IS NULL OR quality >= minQuality)}
 * semantics of the original per-bucket query for any floor value, not just the GOOD/NONE pair.
 *
 * @param jobOrderId the id of the job order the inventory row is linked to.
 * @param materialId the id of the row's material.
 * @param quality the row's quality grade, or {@code null} when ungraded.
 * @param amount the row's stocked amount (SCU); never {@code null} for a persisted row.
 */
public record JobOrderMaterialStockRow(
    UUID jobOrderId, UUID materialId, Integer quality, Double amount) {}
