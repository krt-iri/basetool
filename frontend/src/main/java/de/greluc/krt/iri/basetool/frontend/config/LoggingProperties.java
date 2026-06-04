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

package de.greluc.krt.iri.basetool.frontend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration for structured logging, MDC correlation, slow-request detection and slow
 * WebClient call detection in the frontend module. Bound under {@code app.logging.*} in {@code
 * application*.yml}. Invalid values fail the context start early.
 */
@Getter
@Setter
@ToString
@Validated
@ConfigurationProperties(prefix = "app.logging")
public class LoggingProperties {

  /** HTTP header used to accept an inbound correlation id and echo the effective one back. */
  @NotBlank private String correlationIdHeader = "X-Correlation-Id";

  /** MDC key for the correlation id. Must match the {@code %X{correlationId}} pattern. */
  @NotBlank private String correlationIdMdcKey = "correlationId";

  /** MDC key for the JWT {@code sub} claim. Intentionally no emails/names. */
  @NotBlank private String userIdMdcKey = "userId";

  /** Requests slower than this are logged at WARN by {@code RequestLoggingFilter}. */
  @Min(0)
  private long slowRequestThresholdMs = 2000L;

  /** WebClient calls slower than this are logged at WARN by {@code WebClientLoggingFilter}. */
  @Min(0)
  private long slowBackendCallThresholdMs = 1500L;

  /** Feature flag for structured (JSON) logging, activated in {@code logback-spring.xml}. */
  private boolean structuredEnabled = false;
}
