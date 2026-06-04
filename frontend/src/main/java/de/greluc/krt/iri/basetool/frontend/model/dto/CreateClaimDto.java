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

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code CreateClaimDto}: the create-or-update payload for a
 * material claim. The triple {@code (materialId, qualityRequirement, claimingOrgUnitId)} identifies
 * the bucket+squadron; re-posting with a new {@code amount} updates the existing claim. {@code
 * qualityRequirement} is the {@code GOOD}/{@code NONE} name as a string. No interactive UI produces
 * this yet (Phase 6).
 *
 * @param materialId the material being claimed
 * @param qualityRequirement the quality bucket name ({@code GOOD} or {@code NONE})
 * @param claimingOrgUnitId the squadron making the claim
 * @param amount the claimed partial quantity (strictly positive)
 */
public record CreateClaimDto(
    UUID materialId, String qualityRequirement, UUID claimingOrgUnitId, Double amount) {}
