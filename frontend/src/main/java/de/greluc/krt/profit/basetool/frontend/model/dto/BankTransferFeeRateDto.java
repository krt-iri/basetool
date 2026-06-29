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

/**
 * Frontend mirror of the current in-game transfer-fee rate (ADR-0052, REQ-BANK-033). The
 * account-detail page fetches it and exposes it to {@code bank.js} so the withdraw / transfer
 * modals show a live "Gebühr / wird abgebucht" preview (fee plus the gross debited) as the staffer
 * types the amount; the fee-free holder-to-holder Umbuchung modal has no such preview.
 *
 * @param rate the fee rate as a fraction in {@code [0, 1)} (e.g. {@code 0.005} = 0.5%)
 */
public record BankTransferFeeRateDto(BigDecimal rate) {}
