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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import de.greluc.krt.profit.basetool.backend.model.Material;
import de.greluc.krt.profit.basetool.backend.model.QuantityType;
import de.greluc.krt.profit.basetool.backend.repository.MaterialRepository;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// covers REQ-INV-003 (server-side validation) - see docs/specs/inv-material-quantities.md
@ExtendWith(MockitoExtension.class)
class ValidQuantityAmountValidatorTest {

  @Mock private MaterialRepository materialRepository;

  @Mock private ConstraintValidatorContext context;

  @Mock private ConstraintValidatorContext.ConstraintViolationBuilder builder;

  @Mock
  private ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext
      nodeBuilder;

  private ValidQuantityAmountValidator validator;
  private UUID materialId;

  @BeforeEach
  void setUp() {
    validator = new ValidQuantityAmountValidator(materialRepository);
    materialId = UUID.randomUUID();
  }

  @Test
  void shouldBeValidWhenNull() {
    QuantityAware dto = new TestDto(null, null);
    assertTrue(validator.isValid(dto, context));

    dto = new TestDto(materialId, null);
    assertTrue(validator.isValid(dto, context));

    dto = new TestDto(null, 1.0);
    assertTrue(validator.isValid(dto, context));
  }

  @Test
  void shouldBeInvalidWhenNegativeOrZero() {
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
    when(builder.addPropertyNode("amount")).thenReturn(nodeBuilder);

    QuantityAware dto0 = new TestDto(materialId, 0.0);
    assertFalse(validator.isValid(dto0, context));

    QuantityAware dtoNeg = new TestDto(materialId, -5.0);
    assertFalse(validator.isValid(dtoNeg, context));

    verify(context, times(2)).disableDefaultConstraintViolation();
    verify(context, times(2))
        .buildConstraintViolationWithTemplate("{error.validation.quantity_must_be_positive}");
  }

  @Test
  void shouldBeValidWhenPieceIsInteger() {
    Material material = new Material();
    material.setQuantityType(QuantityType.PIECE);
    when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

    QuantityAware dto = new TestDto(materialId, 5.0);
    assertTrue(validator.isValid(dto, context));
  }

  @Test
  void shouldBeInvalidWhenPieceIsDecimal() {
    Material material = new Material();
    material.setQuantityType(QuantityType.PIECE);
    when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
    when(builder.addPropertyNode("amount")).thenReturn(nodeBuilder);

    QuantityAware dto = new TestDto(materialId, 5.5);
    assertFalse(validator.isValid(dto, context));

    verify(context).disableDefaultConstraintViolation();
    verify(context)
        .buildConstraintViolationWithTemplate("{error.validation.quantity_must_be_integer}");
  }

  @Test
  void shouldBeValidWhenScuHasUpToThreeDecimals() {
    Material material = new Material();
    material.setQuantityType(QuantityType.SCU);
    when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

    assertTrue(validator.isValid(new TestDto(materialId, 10.0), context));
    assertTrue(validator.isValid(new TestDto(materialId, 10.12), context));
    assertTrue(validator.isValid(new TestDto(materialId, 10.123), context));
  }

  @Test
  void shouldBeValidWhenScuHasMoreThanThreeDecimals() {
    // SCU precision is no longer rejected: an amount with more than three decimals is rounded
    // HALF_UP to three places at the persistence boundary (the entity @PrePersist/@PreUpdate
    // hooks), mirroring the frontend, so the validator must accept it rather than refuse it.
    Material material = new Material();
    material.setQuantityType(QuantityType.SCU);
    when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

    assertTrue(validator.isValid(new TestDto(materialId, 10.1234), context));
  }

  record TestDto(UUID materialId, Double amount) implements QuantityAware {}
}
