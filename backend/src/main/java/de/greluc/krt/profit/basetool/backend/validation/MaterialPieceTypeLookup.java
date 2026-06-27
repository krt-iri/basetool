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

import java.util.UUID;

/**
 * Lookup seam the {@link ValidQuantityAmountValidator} uses to learn whether a material is measured
 * in whole PIECEs, without the {@code validation} package depending on {@code model} / {@code
 * repository}.
 *
 * <p>This is a deliberate <b>dependency inversion</b> (REQ-ARCH cycle cleanup): the constraint
 * annotations in {@code model.dto} reference the validator's package, so if the validator itself
 * reached down into {@code repository.MaterialRepository} / {@code model.QuantityType} the slices
 * would form a {@code model} &harr; {@code validation} (and {@code model} &rarr; {@code validation}
 * &rarr; {@code repository} &rarr; {@code model}) package cycle. By depending only on this
 * interface — implemented in the {@code service} layer ({@code MaterialPieceTypeLookupService}) —
 * {@code validation} stays a pure leaf.
 *
 * <p>The method returns a plain {@code boolean} on purpose (not the {@code model.QuantityType}
 * enum) so the interface carries no {@code model} type: an unknown / missing material maps to
 * {@code false}, which the validator treats exactly like a non-PIECE material — validation passes
 * and the surrounding {@code @NotNull} / foreign-key checks report the missing material instead.
 */
public interface MaterialPieceTypeLookup {

  /**
   * Reports whether the given material is measured in whole PIECEs.
   *
   * @param materialId the material id to resolve; may be any UUID, including one that does not
   *     resolve to a material.
   * @return {@code true} iff a material with this id exists and its quantity type is PIECE; {@code
   *     false} when the material is SCU-measured or does not exist.
   */
  boolean isPieceQuantity(UUID materialId);
}
