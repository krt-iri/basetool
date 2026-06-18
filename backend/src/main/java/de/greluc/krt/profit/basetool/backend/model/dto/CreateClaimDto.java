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

import de.greluc.krt.profit.basetool.backend.model.QualityRequirement;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Create-or-update payload for a material claim. The triple {@code (materialId, qualityRequirement,
 * claimingOrgUnitId)} identifies the bucket+squadron — posting it again with a new {@code amount}
 * updates the squadron's existing claim rather than inserting a duplicate (one-claim-per-bucket
 * invariant). To withdraw, the caller uses {@code DELETE /claims/{claimId}} instead of posting a
 * zero amount.
 *
 * @param materialId the material being claimed; must reference a bucket that exists on the order
 * @param qualityRequirement the quality bucket ({@code GOOD} or {@code NONE})
 * @param claimingOrgUnitId the squadron making the claim (must be one the caller may act for)
 * @param amount the claimed partial quantity; strictly positive, and bounded by the bucket's open
 *     remaining (no overclaim) at the service layer
 */
public record CreateClaimDto(
    @NotNull UUID materialId,
    @NotNull QualityRequirement qualityRequirement,
    @NotNull UUID claimingOrgUnitId,
    @NotNull @Positive Double amount) {}
