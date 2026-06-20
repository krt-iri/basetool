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

package de.greluc.krt.profit.basetool.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration for structured logging, MDC correlation and slow-request detection.
 *
 * <p>Bound under the {@code app.logging.*} prefix in {@code application*.yml}. Because all fields
 * are validated via Jakarta-Validation, any misconfiguration fails the application context start
 * early (see {@code LoggingPropertiesBindingTest} for the contract).
 */
@Getter
@Setter
@ToString
@Validated
@ConfigurationProperties(prefix = "app.logging")
public class LoggingProperties {

  /**
   * HTTP header used to accept an inbound correlation id and echo the effective one back. The value
   * is exposed via {@code X-Correlation-Id} by default, matching the widely used de-facto standard
   * across gateways/proxies.
   */
  @NotBlank private String correlationIdHeader = "X-Correlation-Id";

  /**
   * MDC key under which the correlation id is stored for the duration of a request. Must stay in
   * sync with the {@code %X{correlationId}} placeholder in {@code logback-spring.xml}.
   */
  @NotBlank private String correlationIdMdcKey = "correlationId";

  /**
   * MDC key under which the authenticated user's JWT {@code sub} claim is stored. Intentionally
   * limited to {@code sub} to avoid leaking names, emails or token contents into log files.
   */
  @NotBlank private String userIdMdcKey = "userId";

  /**
   * MDC key under which {@link de.greluc.krt.profit.basetool.backend.logging.CorrelationIdFilter}
   * stores the resolved OrgUnit context of the current request — the caller's active OrgUnit
   * (Staffel or Spezialkommando, possibly the union of memberships), or the sentinel {@code
   * anonymous} / {@code none} / {@code all} when no single OrgUnit applies. Keep this in sync with
   * the {@code %X{orgUnitId}} placeholder in {@code logback-spring.xml}.
   */
  @NotBlank private String orgUnitIdMdcKey = "orgUnitId";

  /**
   * Requests taking longer than this threshold (in milliseconds) are logged at {@code WARN} instead
   * of {@code INFO} by {@code RequestLoggingFilter}. Set to a large value to disable.
   */
  @Min(0)
  private long slowRequestThresholdMs = 2000L;

  /**
   * Feature flag for structured (JSON) logging. The {@code logback-spring.xml} activates the JSON
   * appender only when this property is {@code true} (typically in production).
   */
  private boolean structuredEnabled = false;
}
