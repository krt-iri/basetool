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

package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Internal DTO for parsing the root of an uploaded external blueprint export (#327, Phase 4). Both
 * supported exporters — the SCMDB log-watcher and the <a
 * href="https://github.com/krt-iri/basetool-bp-extractor">Basetool Blueprint Extractor</a> — wrap
 * their records in a top-level {@code blueprints} array; every other top-level field (schema
 * version, tool metadata, mission list, player summaries, …) is ignored.
 *
 * @param blueprints the acquired-blueprint entries; {@code null} if the key is absent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BlueprintExportFileDto(
    @JsonProperty("blueprints") List<BlueprintExportEntryDto> blueprints) {}
