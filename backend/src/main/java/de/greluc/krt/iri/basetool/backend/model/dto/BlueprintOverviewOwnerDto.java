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

/**
 * One owner row of the blueprint availability drill-down (#364): the display name of an in-scope
 * member who owns the selected blueprint. Deliberately carries the display name only — never the
 * Keycloak {@code sub} or e-mail — so the overview cannot leak account identifiers.
 *
 * @param ownerName the member's effective display name (display name, or username fallback)
 */
public record BlueprintOverviewOwnerDto(String ownerName) {}
