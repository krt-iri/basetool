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

package de.greluc.krt.profit.basetool.ingest.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Rate-limit budget for the ingest endpoints (prefix {@code app.rate-limit}). The new ingress must
 * not become a way to hammer the backend's import endpoints, so each caller gets a small token
 * bucket refilled on a fixed interval (REQ-INGEST-005). This single budget is applied on two keys:
 * per authenticated JWT subject ({@link
 * de.greluc.krt.profit.basetool.ingest.ratelimit.SubjectRateLimiter}, the enforceable control) and
 * per source IP ({@link de.greluc.krt.profit.basetool.ingest.filter.RateLimitingFilter}, a coarse
 * pre-auth front line). Modelled on the backend's bucket4j limiter but scoped to this module's two
 * endpoints.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

  /** Master switch; set {@code false} (e.g. in the e2e stack) to disable throttling entirely. */
  private boolean enabled = true;

  /** Bucket size: the maximum burst of ingest calls a single caller (subject / IP) may make. */
  @Min(1)
  private int capacity = 30;

  /** Tokens added back to the bucket every {@link #refillPeriod}. */
  @Min(1)
  private int refillTokens = 30;

  /** Refill cadence for {@link #refillTokens}. */
  @NotNull private Duration refillPeriod = Duration.ofMinutes(1);
}
