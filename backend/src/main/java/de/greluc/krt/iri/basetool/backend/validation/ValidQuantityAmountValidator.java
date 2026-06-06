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

package de.greluc.krt.iri.basetool.backend.validation;

import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.QuantityType;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Constraint validator for {@link ValidQuantityAmount}.
 *
 * <p>Resolves the material by id (one DB hit per validated DTO) and applies the
 * quantity-type-specific rules listed on {@link ValidQuantityAmount}: {@code amount > 0} for both
 * types and whole-number amounts for {@code PIECE}. {@code SCU} fractional precision is
 * <em>not</em> rejected here — an amount with more than three decimals is commercially rounded
 * (HALF_UP) to three places at the persistence boundary (the {@code @PrePersist}/{@code @PreUpdate}
 * hooks on the amount entities), mirroring the frontend, so the server accepts and normalises it
 * rather than refusing it. If the material does not exist, validation silently passes — the
 * surrounding {@code @NotNull}/foreign-key checks (or the service layer) report that case so the
 * user gets the right error key, not a confusing "invalid quantity" message.
 */
@Component
@RequiredArgsConstructor
public class ValidQuantityAmountValidator
    implements ConstraintValidator<ValidQuantityAmount, QuantityAware> {

  private final MaterialRepository materialRepository;

  @Override
  public boolean isValid(QuantityAware dto, ConstraintValidatorContext context) {
    if (dto == null || dto.materialId() == null || dto.amount() == null) {
      return true; // Let @NotNull handle these
    }

    if (dto.amount() <= 0) {
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate("{error.validation.quantity_must_be_positive}")
          .addPropertyNode("amount")
          .addConstraintViolation();
      return false;
    }

    Optional<Material> materialOpt = materialRepository.findById(dto.materialId());
    if (materialOpt.isEmpty()) {
      return true; // Let other validations handle missing material
    }

    // The isEmpty()/early-return guard above already excludes the empty case;
    // orElseThrow makes that contract explicit (and silences SpotBugs).
    Material material = materialOpt.orElseThrow();
    if (material.getQuantityType() == QuantityType.PIECE && dto.amount() % 1 != 0) {
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate("{error.validation.quantity_must_be_integer}")
          .addPropertyNode("amount")
          .addConstraintViolation();
      return false;
    }
    // SCU amounts with more than three decimals are NOT rejected: they are rounded HALF_UP to
    // three places at the persistence boundary (see roundAmountToScuScale on the amount entities),
    // so the server normalises fractional precision the same way the frontend does.
    return true;
  }
}
