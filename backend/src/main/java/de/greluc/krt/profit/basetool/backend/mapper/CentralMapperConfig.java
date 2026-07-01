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

package de.greluc.krt.profit.basetool.backend.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Shared MapStruct configuration inherited by every mapper via {@code @Mapper(config =
 * CentralMapperConfig.class)}. It centralises the two settings that were previously repeated on
 * each mapper: {@code componentModel = "spring"} (so MapStruct emits Spring {@code @Component}
 * beans that can be constructor-injected) and {@code unmappedTargetPolicy = IGNORE} (so unmapped
 * target properties are silently skipped instead of failing the build). Pointing every mapper at
 * this config replaces the per-mapper boilerplate, which previously spelled the policy three
 * different ways ({@code ReportingPolicy.IGNORE}, {@code org.mapstruct.ReportingPolicy.IGNORE}, and
 * a bare imported {@code IGNORE}), with a single authoritative declaration.
 */
@MapperConfig(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CentralMapperConfig {}
