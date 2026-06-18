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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Frontend mirror of one dashboard KPI card (D1 mockup): balance, sign-colored 30-day delta and the
 * end-of-day balance series the page controller scales into the inline-SVG sparkline polyline.
 *
 * @param id the account's id (the card links to the detail page)
 * @param accountNo the account's display number
 * @param name the account's display name
 * @param type account type enum name (rendered as chip)
 * @param status lifecycle enum name — closed cards render dimmed
 * @param balance current balance
 * @param delta30d net change over the last 30 days (signed)
 * @param sparkline end-of-day balances of the last 30 days, oldest first
 */
public record BankDashboardAccountDto(
    UUID id,
    String accountNo,
    String name,
    String type,
    String status,
    BigDecimal balance,
    BigDecimal delta30d,
    List<BigDecimal> sparkline) {}
