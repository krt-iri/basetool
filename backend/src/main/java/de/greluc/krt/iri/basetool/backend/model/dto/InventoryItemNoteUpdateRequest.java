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
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;

/**
 * Request payload for creating, updating or removing the free-text note on an {@code
 * InventoryItem}.
 *
 * <p>An empty or blank {@code note} value is normalized to {@code null} by the service layer,
 * effectively removing any existing note. The {@code version} field carries the JPA entity version
 * for optimistic locking.
 */
public record InventoryItemNoteUpdateRequest(
    @Nullable @Size(max = 1000) String note, @NotNull Long version) {}
