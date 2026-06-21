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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Internal DTO for parsing a single blueprint entry from an uploaded external blueprint export
 * (#327, Phase 4; scmdb.net export added later). Three exporters are supported and share this
 * shape: the SCMDB log-watcher, the <a
 * href="https://github.com/krt-profit/basetool-bp-extractor">Basetool Blueprint Extractor</a>, and
 * the <a href="https://scmdb.net">scmdb.net</a> profile / tracking export (REQ-INV-014).
 *
 * <p>The first two tools read the same Star Citizen {@code Game.log} {@code "Received Blueprint:
 * <name>"} notification, so the {@link #productName} is identical between them; they differ only in
 * how they stamp the acquisition time — SCMDB writes {@link #ts} (fractional Unix epoch seconds),
 * the Blueprint Extractor writes {@link #receivedAt} (ISO-8601 UTC instant). The scmdb.net export
 * is a manually-curated <em>checklist</em> instead of a log capture: each entry names the crafted
 * product under {@code name} (mapped onto {@link #productName} via {@link JsonAlias}), carries the
 * structural DataForge blueprint key under {@link #tag} (= a blueprint's {@code scwiki_key}, used
 * for the high-confidence tag match — REQ-INV-019), a {@link #completed} flag that is {@code false}
 * for blueprints the user has <em>not</em> yet unlocked (skipped by the import), and no acquisition
 * timestamp at all. The import consumes whichever timestamp is present (preferring {@code ts}) and
 * ignores every other field a given exporter writes (category, player, mission correlation, url,
 * favorite, …).
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} keeps the parse tolerant towards new
 * fields so a future exporter release does not break the import.
 *
 * @param productName the crafted product's name (matched against the master product list); accepts
 *     the scmdb.net {@code name} key as an alias of the watcher/extractor {@code productName} key.
 *     No exporter emits both keys; were a hypothetical mixed entry to carry both, Jackson binds the
 *     one appearing last in the document (last-wins), which is acceptable since the values would
 *     denote the same product
 * @param ts SCMDB acquisition timestamp as fractional Unix epoch seconds, or {@code null} if absent
 * @param receivedAt Blueprint Extractor acquisition timestamp as an ISO-8601 instant string (e.g.
 *     {@code 2026-03-26T16:49:31.050Z}), or {@code null} if absent
 * @param tag scmdb.net DataForge blueprint key (e.g. {@code
 *     BP_CRAFT_behr_rifle_ballistic_02_civilian}), matched case-insensitively against a blueprint's
 *     {@code scwiki_key} for the structural tag match; {@code null} in the watcher / extractor
 *     exports, which do not carry it
 * @param completed scmdb.net unlock flag: {@code false} marks a blueprint the user has not unlocked
 *     yet (the import skips it); {@code null} (watcher / extractor exports, which list only
 *     acquired blueprints) and {@code true} are both treated as owned
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BlueprintExportEntryDto(
    @JsonProperty("productName") @JsonAlias({"name"}) String productName,
    @JsonProperty("ts") Double ts,
    @JsonProperty("receivedAt") String receivedAt,
    @JsonProperty("tag") String tag,
    @JsonProperty("completed") Boolean completed) {}
