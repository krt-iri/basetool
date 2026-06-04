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
 * Read DTO mirroring the backend {@code PersonalBlueprintResponse} (#327): one blueprint the
 * calling user owns. {@code ownerSub} is intentionally absent — it is never exposed at the
 * boundary.
 *
 * @param id entry id
 * @param productKey normalized product key
 * @param productName canonical display name
 * @param outputItemId resolved output {@code game_item} id, or {@code null}
 * @param acquiredAt in-game acquisition time, or {@code null}
 * @param note free-form note, or {@code null}
 * @param version optimistic-lock version echoed back on update
 * @param createdAt row creation timestamp
 * @param updatedAt row last-update timestamp
 */
public record PersonalBlueprintDto(
    UUID id,
    String productKey,
    String productName,
    UUID outputItemId,
    Instant acquiredAt,
    String note,
    Long version,
    Instant createdAt,
    Instant updatedAt) {}
