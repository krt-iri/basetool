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

package de.greluc.krt.iri.basetool.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Write payload for a bank employee confirming a pending booking request (REQ-BANK-023): the holder
 * the employee records as having received the deposit / paid the withdrawal out, plus the echoed
 * optimistic-locking version to detect a concurrent decision.
 *
 * @param holderId the holder recorded for the booked transaction
 * @param version the request's echoed {@code @Version}; a mismatch surfaces as 409
 */
public record ConfirmBankBookingRequest(@NotNull UUID holderId, @NotNull Long version) {}
