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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Update payload for {@code PUT /api/v1/material-external-aliases/{id}}.
 *
 * <p>Same shape as {@link MaterialExternalAliasCreateRequest} but additionally carries the
 * optimistic-lock {@code version} that the service compares against the row's current version
 * before accepting the change. A mismatched version surfaces as {@link
 * org.springframework.orm.ObjectOptimisticLockingFailureException} → HTTP 409.
 *
 * @param materialId UUID of the local material to link to (required)
 * @param sourceSystem catalogue identifier, must be {@code "UEX"} or {@code "SCWIKI"}
 * @param externalName the external commodity name (required, max 255 chars)
 * @param externalKey optional external internal key (max 255 chars)
 * @param externalUuid optional external UUID
 * @param externalCode optional external short code (max 64 chars)
 * @param note optional provenance note (free-form text)
 * @param version optimistic-lock token from the GET response
 */
public record MaterialExternalAliasUpdateRequest(
    @NotNull UUID materialId,
    @NotBlank @Pattern(regexp = "UEX|SCWIKI") String sourceSystem,
    @NotBlank @Size(max = 255) String externalName,
    @Size(max = 255) String externalKey,
    UUID externalUuid,
    @Size(max = 64) String externalCode,
    String note,
    @NotNull Long version) {}
