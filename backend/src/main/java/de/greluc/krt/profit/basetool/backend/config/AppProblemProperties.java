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

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties under {@code app.problems.*}.
 *
 * <p>Centralizes the base URI used to build RFC&nbsp;7807 {@code type} values (e.g. {@code
 * https://profit-base.online/problems/not-found}). Keeping the prefix here lets us repoint
 * problem-type URIs per environment without hunting through {@code GlobalExceptionHandler} for
 * hardcoded strings.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.problems")
public class AppProblemProperties {
  /** Base URI for Problem Detail types. */
  @NotBlank private String baseUri = "https://profit-base.online/problems/";
}
