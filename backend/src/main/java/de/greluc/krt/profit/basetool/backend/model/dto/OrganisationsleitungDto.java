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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Wire shape for {@link de.greluc.krt.profit.basetool.backend.model.Organisationsleitung} (epic
 * #692, REQ-ORG-014) — the single top-of-hierarchy org unit. Like a Bereich it carries no
 * profit-eligibility or promotion flag, and unlike a Bereich it has <em>no</em> parent (the OL is
 * the root; {@code chk_org_unit_ol_has_no_parent} enforces this at the data layer).
 *
 * @param id OL identifier; {@code null} on create requests, populated on responses.
 * @param name display name; case-insensitive unique across all org-unit kinds. Required, max 255.
 * @param shorthand short tag; unique across all kinds. Required, max 255.
 * @param description free-form text; nullable.
 * @param active soft-delete flag; server-populated, {@code null} on requests means "no change".
 * @param version optimistic-lock counter; server-populated on create + read, required on update.
 */
public record OrganisationsleitungDto(
    UUID id,
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 255) String shorthand,
    @Size(max = 65_535) String description,
    Boolean active,
    Long version) {}
