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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe App Http configuration properties, bound through the canonical record constructor.
 * Component-level {@link DefaultValue} annotations preserve the previous field-initializer defaults
 * (connect 3s, the rest 5s) when the corresponding {@code app.http.*} key is absent.
 *
 * @param connectTimeout WebClient connect timeout
 * @param responseTimeout overall WebClient response timeout
 * @param readTimeout WebClient socket read timeout
 * @param writeTimeout WebClient socket write timeout
 */
@Validated
@ConfigurationProperties(prefix = "app.http")
public record AppHttpProperties(
    @NotNull @DefaultValue("3s") Duration connectTimeout,
    @NotNull @DefaultValue("5s") Duration responseTimeout,
    @NotNull @DefaultValue("5s") Duration readTimeout,
    @NotNull @DefaultValue("5s") Duration writeTimeout) {}
