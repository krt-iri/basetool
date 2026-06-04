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

package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Form-binding object for Job Type input. */
public record JobTypeForm(
    @NotBlank(message = "{validation.name.required}") @Size(max = 255) String name,
    @Size(max = 1000) String description,
    @NotBlank(message = "{validation.archetype.required}") @Size(max = 50) String archetype,
    Boolean isLeadershipRole,
    Long version) {
  public Boolean getIsLeadershipRole() {
    return isLeadershipRole;
  }
}
