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

package de.greluc.krt.profit.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Internal DTO for parsing the root of an uploaded external blueprint export (#327, Phase 4). Both
 * supported exporters — the SCMDB log-watcher and the <a
 * href="https://github.com/krt-profit/basetool-bp-extractor">Basetool Blueprint Extractor</a> —
 * wrap their records in a top-level {@code blueprints} array; every other top-level field (schema
 * version, tool metadata, mission list, player summaries, …) is ignored.
 *
 * <p>{@code additionalSourceFolders} is mirrored from the Blueprint Extractor's {@code
 * BlueprintExport} contract for explicitness only — the import never consumes it. The extractor
 * keeps its export schema at version 1 and evolves it additively (same rule as ADR-0008 for the
 * refinery extract; precedent: {@code capturedAt} on {@code sourceImages}), so new nullable
 * envelope fields like this one must parse without a schema bump while exports from older extractor
 * versions, which lack the key entirely, stay accepted.
 *
 * @param blueprints the acquired-blueprint entries; {@code null} if the key is absent
 * @param additionalSourceFolders extra game-channel folders the Blueprint Extractor scanned beside
 *     its primary {@code sourceFolder} (currently the {@code HOTFIX} sibling of {@code LIVE});
 *     {@code null} when only the primary folder was scanned, when an older extractor wrote the
 *     export, or in SCMDB log-watcher exports — the extractor serializes the key even when {@code
 *     null} (it encodes defaults). Provenance only, not consumed by the import
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BlueprintExportFileDto(
    @JsonProperty("blueprints") List<BlueprintExportEntryDto> blueprints,
    @JsonProperty("additionalSourceFolders") List<String> additionalSourceFolders) {}
