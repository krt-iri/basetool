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

package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.validation.QuantityAware;
import de.greluc.krt.iri.basetool.backend.validation.ValidQuantityAmount;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request DTO for a single entry in the store dialog of a refinery order.
 *
 * <p>The amount ({@code amount}) is finally set by the user when storing and overrides the output
 * amount originally calculated by the refinery order (see {@code
 * RefineryOrderService#storeRefineryOrder}). Amount validation (decimal number, &gt;= 0, max. 3
 * decimal places) is uniformly applied across the project via {@link ValidQuantityAmount} / {@link
 * QuantityAware}.
 *
 * <p>The optional {@code note} is propagated directly to the resulting {@code InventoryItem} and
 * lets the user attach remarks already at the time of storage.
 *
 * <p>The optional {@code owningOrgUnitId} is the picker output that stamps the resulting {@code
 * InventoryItem}'s owning OrgUnit, validated against the receiving user's ({@code userId}, else the
 * order owner's) memberships by {@code OwnerScopeService.resolveOrgUnitForPickerOutputNullable}. It
 * is required only when that receiving user belongs to more than one OrgUnit (the §5.5.1 matrix's
 * "&gt;1 + no output → 400" branch); for a single-membership or membershipless receiver it may stay
 * {@code null} and the resolver auto-stamps (or leaves the row ownerless). The store dialog
 * pre-fills it with the order's own owning OrgUnit, so a same-OrgUnit self-store needs no manual
 * choice.
 */
@ValidQuantityAmount
public record RefineryOrderStoreItemDto(
    @NotNull UUID materialId,
    @NotNull UUID locationId,
    @NotNull @Min(0) @Max(1000) Integer quality,
    @NotNull Double amount,
    UUID userId,
    UUID jobOrderId,
    @Size(max = 1000) String note,
    UUID owningOrgUnitId)
    implements QuantityAware {}
