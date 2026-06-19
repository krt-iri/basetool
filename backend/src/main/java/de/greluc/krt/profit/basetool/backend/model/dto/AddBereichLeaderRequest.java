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

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body to add (or set the role of) a Bereichsleitung member (epic #692, REQ-ORG-017). The
 * user must hold no Staffel membership (enforced by the service guard and the V165 trigger). If the
 * user already has a membership on the Bereich (e.g. an organisational SK-Leiter seat), the role
 * flag is set on that row; otherwise a new membership is created.
 *
 * @param userId the user to grant the Bereichsleitung role to; required.
 * @param role which Bereichsleitung role (Leiter / Koordinator / Operator); required.
 */
public record AddBereichLeaderRequest(@NotNull UUID userId, @NotNull BereichLeadershipRole role) {}
