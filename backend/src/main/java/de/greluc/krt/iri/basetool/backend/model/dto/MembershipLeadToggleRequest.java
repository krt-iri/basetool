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

import jakarta.validation.constraints.NotNull;

/**
 * Request body for the ADMIN-only Spezialkommando-Lead toggle endpoint {@code PATCH
 * /api/v1/special-commands/{id}/members/{userId}/lead}. Single-field record so the wire format can
 * grow later (e.g. an effective-from date) without breaking existing clients.
 *
 * <p>Carrying the {@link #version} on the toggle is required so a concurrent flag patch on the same
 * membership row (via {@link MembershipFlagsPatchRequest}) surfaces as a 409 here too — promotions
 * and demotions are infrequent but the audit trail must not lose either.
 *
 * @param isLead new value of the {@code is_lead} flag; {@code true} promotes the member to Lead,
 *     {@code false} demotes them.
 * @param version current optimistic-lock version held by the client; required.
 */
public record MembershipLeadToggleRequest(@NotNull Boolean isLead, @NotNull Long version) {}
