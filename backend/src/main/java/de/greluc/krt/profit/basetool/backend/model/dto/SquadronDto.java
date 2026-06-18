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

import java.util.UUID;

/**
 * Data transfer record carrying Squadron payload.
 *
 * <p>{@code isPromotionEnabled} is the per-squadron feature flag for the entire promotion subsystem
 * (topics, categories, level-contents, rank-requirements, member-evaluations). Flag is read-only on
 * the regular update path; admins toggle it through a dedicated {@code
 * /api/v1/squadrons/{id}/promotion-enabled} endpoint so the change is auditable and cannot be made
 * by accident as a side-effect of editing the squadron name/shorthand. {@code null} on the request
 * side is therefore meaningless — the value is always supplied on responses, and only the dedicated
 * toggle endpoint actually mutates it.
 *
 * @param id squadron identifier; nullable on create-request payloads
 * @param name display name (case-insensitive unique)
 * @param shorthand short tag used on badges / column headers
 * @param description free-form text
 * @param active soft-delete flag; {@code true} for the active reference data
 * @param isPromotionEnabled per-squadron promotion-system feature flag (default {@code true})
 * @param isProfitEligible per-squadron Job-Order processor eligibility flag (default {@code
 *     false}); read-only on the regular update path and toggled only through {@code
 *     /api/v1/squadrons/{id}/profit-eligible}
 * @param version optimistic-lock counter
 */
public record SquadronDto(
    UUID id,
    String name,
    String shorthand,
    String description,
    Boolean active,
    Boolean isPromotionEnabled,
    Boolean isProfitEligible,
    Long version) {}
