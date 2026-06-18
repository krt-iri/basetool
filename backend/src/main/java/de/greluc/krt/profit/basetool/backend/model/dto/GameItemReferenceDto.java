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
 * Slim reference projection of a {@code GameItem} for item-order payloads and the orderable-item
 * picker. Carries only what the order UI needs to identify and label a finished item — its id,
 * name, and {@code kind} (the {@code GameItemKind} name, exposed as a string for API stability) —
 * without dragging the full catalogue entity across the boundary.
 *
 * @param id the game item's primary key
 * @param name the item's display name
 * @param kind the {@code GameItemKind} name (e.g. {@code WEAPON}, {@code ARMOR})
 */
public record GameItemReferenceDto(UUID id, String name, String kind) {}
