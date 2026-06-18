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

import java.time.Instant;
import java.util.UUID;

/**
 * Frontend mirror of the backend {@code MaterialExternalAliasDto}. Lives in the frontend module so
 * {@code AdminMaterialAliasesPageController} can deserialise the REST response without pulling in
 * the backend module (the two modules are isolated by Gradle).
 *
 * <p>Fields and types must stay in lockstep with {@code
 * de.greluc.krt.profit.basetool.backend.model.dto.MaterialExternalAliasDto} — any change on the
 * backend side requires a matching change here in the same commit (mirror-DTO rule).
 *
 * @param id alias UUID
 * @param version optimistic-lock token
 * @param materialId linked material UUID (FK)
 * @param materialName linked material name (denormalised for table rendering)
 * @param sourceSystem catalogue identifier ({@code "UEX"}, {@code "SCWIKI"} or {@code
 *     "REFINERY_SCREEN"})
 * @param externalName commodity name in the external catalogue
 * @param externalKey optional external internal key
 * @param externalUuid optional external UUID
 * @param externalCode optional external short code
 * @param note free-form provenance note
 * @param createdBy {@code "system"} for V108 seeds, JWT {@code sub} otherwise
 * @param createdAt row creation timestamp
 * @param updatedAt row last-update timestamp
 */
public record MaterialExternalAliasDto(
    UUID id,
    Long version,
    UUID materialId,
    String materialName,
    String sourceSystem,
    String externalName,
    String externalKey,
    UUID externalUuid,
    String externalCode,
    String note,
    String createdBy,
    Instant createdAt,
    Instant updatedAt) {}
