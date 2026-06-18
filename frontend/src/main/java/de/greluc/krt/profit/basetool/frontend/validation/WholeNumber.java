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

package de.greluc.krt.profit.basetool.frontend.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Asserts that a {@link java.math.BigDecimal} form amount is a whole number (no fractional part).
 *
 * <p>Value-based, not scale-based: {@code 500}, {@code 500.00} and {@code 500.0000} are accepted
 * and only genuinely fractional values such as {@code 500.50} are rejected — unlike
 * {@code @Digits(fraction = 0)}, which would reject the whole-valued {@code 500.00}. Mirrors the
 * backend {@code WholeNumber} constraint so the mission-finance form rejects fractional input
 * inline (REQ-MISSION-001) before the controller calls the backend. {@code null} is valid; presence
 * is the concern of {@code @NotNull} and the lower bound that of {@code @DecimalMin}.
 */
@Documented
@Constraint(validatedBy = WholeNumberValidator.class)
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
public @interface WholeNumber {

  /**
   * The violation message template surfaced when the amount has a fractional part.
   *
   * @return the message template (resolved against the frontend message bundle)
   */
  String message() default "{finance.validation.amount.whole}";

  /**
   * The validation groups this constraint participates in.
   *
   * @return the constraint groups (empty by default)
   */
  Class<?>[] groups() default {};

  /**
   * Carrier for custom payload metadata attached to the constraint.
   *
   * @return the payload types (empty by default)
   */
  Class<? extends Payload>[] payload() default {};
}
