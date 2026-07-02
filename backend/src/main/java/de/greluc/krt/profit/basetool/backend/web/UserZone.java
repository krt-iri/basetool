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

package de.greluc.krt.profit.basetool.backend.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the caller's {@code X-User-Time-Zone} request header, parsed to a {@link java.time.ZoneId},
 * to a controller method parameter — replacing the duplicated {@code parse}/{@code parseZone}
 * header try/catch helpers scattered across the PDF-export controllers.
 *
 * <p>Resolved by {@link UserZoneArgumentResolver}: an absent, blank or invalid IANA zone yields
 * {@code null} (the report services then render in UTC) rather than failing the request — the
 * "silently fall back to UTC" contract the hand-rolled parsers shared. Sites keep the header
 * documented in the OpenAPI output via an explicit {@code @Parameter(in = HEADER)} so the generated
 * document is unchanged.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UserZone {}
