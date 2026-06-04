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

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer record carrying Operation payload — frontend mirror of the backend record.
 *
 * <p>{@code payoutPreliminary} is populated by the backend only on the {@code GET
 * /api/v1/operations/{id}} detail endpoint and is {@code null} otherwise. The operation-detail
 * Thymeleaf template reads it to render the "payout figures are preliminary" warning banner above
 * the payout table when at least one mission of the operation still lacks an actual start or end
 * time. Treat {@code null} as "unknown" and hide the banner.
 *
 * <p>Field order intentionally matches the backend record (id, name, description, status,
 * owningSquadron, version, createdAt, updatedAt, payoutPreliminary) so the Jackson wire-shape stays
 * positional-positional and {@link DtoMirrorConsistencyTest} can compare component-by-component
 * without exception.
 *
 * @param id operation primary key
 * @param name operation name
 * @param description optional free-text description
 * @param status current operation status (string mirror of the backend enum)
 * @param owningSquadron squadron that owns the operation (multi-tenant scope marker)
 * @param version optimistic-lock version
 * @param createdAt creation timestamp (UTC); not rendered by any template today but mirrored to
 *     keep the wire-shape symmetric with the backend record
 * @param updatedAt last-update timestamp (UTC); same rationale as {@code createdAt}
 * @param payoutPreliminary {@code true} when the backend reports that at least one mission has no
 *     {@code actualStartTime} or {@code actualEndTime}; {@code null} when not computed
 */
public record OperationDto(
    UUID id,
    String name,
    String description,
    String status,
    SquadronReferenceDto owningSquadron,
    Long version,
    Instant createdAt,
    Instant updatedAt,
    Boolean payoutPreliminary) {}
