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

package de.greluc.krt.profit.basetool.frontend.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/** Type-safe App Http configuration properties. */
@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "app.http")
public class AppHttpProperties {
  @NotNull private Duration connectTimeout = Duration.ofSeconds(3);

  @NotNull private Duration responseTimeout = Duration.ofSeconds(5);

  @NotNull private Duration readTimeout = Duration.ofSeconds(5);

  @NotNull private Duration writeTimeout = Duration.ofSeconds(5);
}
