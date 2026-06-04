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

package de.greluc.krt.iri.basetool.backend.config;

import de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

/**
 * SpringDoc customizer that mirrors the project's {@link ApiDeprecation} + {@code @Deprecated}
 * metadata into the generated OpenAPI document.
 *
 * <p>The {@link de.greluc.krt.iri.basetool.backend.interceptor.DeprecationInterceptor} already
 * emits the runtime headers; this customizer keeps the static spec in sync so the generated {@code
 * openapi.json} marks the operation {@code deprecated} and adds a {@code **DEPRECATED**} block to
 * the description with the sunset date and the recommended replacement path.
 */
@Configuration
public class OpenApiDeprecationConfig {

  /**
   * Returns SpringDoc operation customizer that annotates deprecated endpoints in the OpenAPI doc,
   * respecting both the project-specific {@link ApiDeprecation} and the JDK {@code @Deprecated} on
   * the handler method or its declaring class.
   *
   * @return SpringDoc operation customizer that annotates deprecated endpoints in the OpenAPI doc,
   *     respecting both the project-specific {@link ApiDeprecation} and the JDK {@code @Deprecated}
   *     on the handler method or its declaring class
   */
  @Bean
  public OperationCustomizer deprecationCustomizer() {
    return (Operation operation, HandlerMethod handlerMethod) -> {
      ApiDeprecation deprecation = handlerMethod.getMethodAnnotation(ApiDeprecation.class);
      boolean isDeprecated = handlerMethod.hasMethodAnnotation(Deprecated.class);

      if (deprecation == null) {
        deprecation = handlerMethod.getBeanType().getAnnotation(ApiDeprecation.class);
      }
      if (!isDeprecated) {
        isDeprecated = handlerMethod.getBeanType().isAnnotationPresent(Deprecated.class);
      }

      if (isDeprecated || deprecation != null) {
        operation.setDeprecated(true);

        if (deprecation != null) {
          StringBuilder desc = new StringBuilder();
          if (operation.getDescription() != null && !operation.getDescription().isBlank()) {
            desc.append(operation.getDescription()).append("\n\n");
          }
          desc.append("**DEPRECATED**");
          if (!deprecation.sunset().isEmpty()) {
            desc.append("\n- Sunset Date: ").append(deprecation.sunset());
          }
          if (!deprecation.replacement().isEmpty()) {
            desc.append("\n- Replacement: `").append(deprecation.replacement()).append("`");
          }
          operation.setDescription(desc.toString().trim());
        }
      }
      return operation;
    };
  }
}
