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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * Frontend mirror of the backend {@code ShipRequestDto} wire shape (per the {@code
 * feedback_backend_frontend_dto_mirror} memory: backend + frontend records must stay aligned
 * field-for-field, or a render-time 500 surfaces in prod).
 *
 * <p>R5.d.f added the trailing {@code owningOrgUnitId} picker output. {@code null} triggers the
 * backend resolver's auto-stamp branch (single-membership users); a non-null id routes through the
 * shared resolver which validates it against the target user's {@code org_unit_membership} rows.
 */
public record ShipRequestDto(
    String name,
    @NotNull(message = "{ship.validation.shiptype.required}") UUID shipTypeId,
    @NotBlank(message = "{ship.validation.insurance.required}")
        @Pattern(
            regexp = "^(0|([1-9]|[1-9][0-9]|1[0-1][0-9]|120)|LTI)$",
            message = "{validation.insurance.pattern}")
        String insurance,
    UUID locationId,
    boolean fitted,
    Long version,
    UUID owningOrgUnitId) {}
