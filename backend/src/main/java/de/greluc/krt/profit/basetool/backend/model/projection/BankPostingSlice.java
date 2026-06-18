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

package de.greluc.krt.profit.basetool.backend.model.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPQL constructor projection of one posting reduced to the dashboard-relevant columns. The
 * dashboard fetches all postings of the visible accounts inside the 30-day window in ONE query
 * (REQ-BANK-016, no N+1) and derives the per-account delta, in/out totals and the daily sparkline
 * series in memory — at org scale the window contains few rows, so shipping the slice beats three
 * extra grouped statements.
 *
 * @param accountId the account the posting belongs to
 * @param createdAt the booking instant (UTC), used for the daily bucketing
 * @param amount the signed posting amount
 */
public record BankPostingSlice(UUID accountId, Instant createdAt, BigDecimal amount) {}
