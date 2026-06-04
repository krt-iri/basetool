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

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Frontend mirror of the backend {@code InventoryItemBookOutDto} (per the {@code
 * feedback_backend_frontend_dto_mirror} memory).
 *
 * <p>R5.d.g added the trailing {@code targetOwningOrgUnitId} picker output. Only honoured for
 * {@link CheckoutType#TRANSFER}; ignored for {@link CheckoutType#DISCARD} and {@link
 * CheckoutType#SELL}.
 */
public record InventoryItemBookOutDto(
    @NotNull @Min(0) Double amount,
    UUID targetUserId,
    UUID targetLocationId,
    CheckoutType type,
    String terminal,
    @Min(0) BigDecimal sellAmount,
    @NotNull Long version,
    UUID targetOwningOrgUnitId) {}
