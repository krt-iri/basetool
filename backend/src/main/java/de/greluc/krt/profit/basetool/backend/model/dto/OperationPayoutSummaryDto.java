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

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregated payout view for an operation: the per-participant breakdown plus the operation-wide
 * donation total.
 *
 * <p>{@code totalDonations} is the sum of every row's {@link OperationPayoutDto#donatedAmount()} —
 * the pool share that DONATE participants contributed to the org instead of receiving it. It is
 * derived from the same rows it ships with, so the central figure on the operation-detail page
 * always equals the sum of the per-donor donated amounts in the table. Donations are retained
 * centrally and are never redistributed to PAYOUT participants — a PAYOUT participant's share is
 * their own attendance slice of the full pool, unaffected by who donates.
 *
 * @param totalDonations operation-wide sum of donated shares (always &gt;= 0, two-decimal scale);
 *     {@link BigDecimal#ZERO} when no participant donated
 * @param payouts the per-participant payout rows, sorted by participant name
 */
public record OperationPayoutSummaryDto(
    BigDecimal totalDonations, List<OperationPayoutDto> payouts) {}
