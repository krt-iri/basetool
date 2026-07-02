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

package de.greluc.krt.profit.basetool.backend.validation;

/**
 * Jakarta Bean Validation group marking constraints that apply only on an <em>update</em>, not on a
 * create — the seam that lets a single {@code XxxWriteRequest} serve both operations (S13, #919).
 *
 * <p>A collapsed write DTO carries the optimistic-lock version as {@code @NotNull(groups =
 * OnUpdate.class) Long version}: the create endpoint validates with {@code @Valid} (the {@code
 * jakarta.validation.groups.Default} group only), so a missing version is accepted; the update
 * endpoint validates with {@code @Validated(&#123;Default.class, OnUpdate.class&#125;)}, so both
 * the base field constraints and the required-version constraint fire and a missing version
 * surfaces as the same {@code VALIDATION_FAILED} 400 it did when Update was its own DTO.
 */
public interface OnUpdate {}
