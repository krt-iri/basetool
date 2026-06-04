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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Wire shape for {@link de.greluc.krt.iri.basetool.backend.model.SpecialCommand}. Mirrors the
 * {@link SquadronDto} contract field-for-field except for the {@code isPromotionEnabled} toggle
 * which is permanently disabled on Spezialkommandos (enforced at the DB layer by the V94 CHECK
 * constraint plus the {@link de.greluc.krt.iri.basetool.backend.model.SpecialCommand} setter
 * override) and therefore omitted from this record — exposing the flag would only let an admin
 * receive a constant {@code false} value with no path to flip it.
 *
 * <p>{@code name} and {@code shorthand} are bean-validated on the inbound (create/update) path so
 * the controller's {@code @Valid} annotation surfaces the failure as a 400 Problem Detail before
 * the service layer attempts the case-insensitive uniqueness lookup.
 *
 * @param id Spezialkommando identifier; nullable on create-request payloads (the server stamps a
 *     fresh UUID then), required on update-request payloads, always populated on responses.
 * @param name display name; case-insensitive unique across {@link
 *     de.greluc.krt.iri.basetool.backend.model.OrgUnitKind#SPECIAL_COMMAND} AND {@link
 *     de.greluc.krt.iri.basetool.backend.model.OrgUnitKind#SQUADRON} rows (the underlying {@code
 *     org_unit.name} UNIQUE constraint spans both kinds). Required, max 255 chars.
 * @param shorthand short tag used on chips / badges; case-insensitive unique across both kinds for
 *     the same reason as {@link #name}. Required, max 255 chars.
 * @param description free-form text; nullable.
 * @param active soft-delete flag; {@code true} for active rows. Populated by the server; {@code
 *     null} on requests means "no change".
 * @param isProfitEligible per-SK Job-Order processor eligibility flag (default {@code false}); only
 *     profit-eligible Spezialkommandos appear in the responsible (processing) picker. Read-only on
 *     the regular update path; toggled only through {@code
 *     /api/v1/special-commands/{id}/profit-eligible}.
 * @param version optimistic-lock counter; required on update so the persistence layer can detect
 *     concurrent edits, server-populated on create + read.
 */
public record SpecialCommandDto(
    UUID id,
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 255) String shorthand,
    @Size(max = 65_535) String description,
    Boolean active,
    Boolean isProfitEligible,
    Long version) {}
