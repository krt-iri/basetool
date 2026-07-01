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

package de.greluc.krt.profit.basetool.frontend.model.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a frontend DTO record component that intentionally mirrors a backend <em>enum</em> property
 * as a plain {@link String} (the raw enum name) rather than a typed Java enum.
 *
 * <p>The frontend hand-mirrors backend DTOs with no shared module and no code generation. It
 * deliberately demotes some backend enums to {@code String} so the enum name can feed an i18n
 * message key directly (e.g. {@code bank.account.type.ORG_UNIT}); a typed enum would fight that
 * key-construction rendering. Such a demotion is legitimate — but it must be a <b>choice</b>, not
 * silent drift.
 *
 * <p>{@code FrontendDtoContractTest} diffs every frontend DTO record against the committed {@code
 * backend/src/main/resources/api/openapi.json}: whenever the schema types a property as an enum but
 * the frontend component is a {@code String}, the test requires this annotation. An un-annotated
 * enum-to-String demotion fails the build, so a backend field that quietly gained or lost enum
 * typing can no longer slip through unnoticed as a runtime template miss.
 *
 * @see de.greluc.krt.profit.basetool.frontend.model.dto
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface BackendEnumAsString {

  /**
   * Optional note documenting the demotion — typically the i18n key prefix the enum name feeds
   * (e.g. {@code "bank.account.type"}). Purely informational: the contract test only checks that
   * the annotation is present, not its value.
   *
   * @return the documenting note, or the empty string when none is given
   */
  String value() default "";
}
