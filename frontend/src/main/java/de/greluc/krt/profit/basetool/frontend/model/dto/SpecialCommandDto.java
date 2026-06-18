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

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code SpecialCommandDto}. Mirrors {@link SquadronDto}
 * field-for-field except for the {@code isPromotionEnabled} field — that flag is permanently {@code
 * false} on every Spezialkommando row by the V94 CHECK constraint and the {@code SpecialCommand}
 * setter override, so exposing it on the wire would only let the admin UI receive a constant {@code
 * false} value with no path to flip it.
 *
 * @param id Spezialkommando identifier; nullable on create payloads.
 * @param name display name.
 * @param shorthand short tag used on chips / badges.
 * @param description free-form text; nullable.
 * @param active soft-delete flag; {@code true} for the active reference data.
 * @param isProfitEligible per-SK Job-Order processor eligibility flag; the admin toggle lives at
 *     {@code /api/proxy/special-commands/{id}/profit-eligible} and decides whether the SK appears
 *     in the Job-Order responsible (processing) picker.
 * @param version optimistic-lock counter.
 */
public record SpecialCommandDto(
    UUID id,
    String name,
    String shorthand,
    String description,
    Boolean active,
    Boolean isProfitEligible,
    Long version) {}
