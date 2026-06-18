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

/**
 * Internal DTO for parsing a single blueprint entry from an uploaded external blueprint export
 * (#327, Phase 4). Two exporters are supported and share this shape: the SCMDB log-watcher and the
 * <a href="https://github.com/krt-profit/basetool-bp-extractor">Basetool Blueprint Extractor</a>.
 *
 * <p>Both tools read the same Star Citizen {@code Game.log} {@code "Received Blueprint: <name>"}
 * notification, so the {@link #productName} is identical between them; they differ only in how they
 * stamp the acquisition time — SCMDB writes {@link #ts} (fractional Unix epoch seconds), the
 * Blueprint Extractor writes {@link #receivedAt} (ISO-8601 UTC instant). The import consumes
 * whichever is present (preferring {@code ts}) and ignores every other field a given exporter
 * writes (category, player, mission correlation, …).
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} keeps the parse tolerant towards new
 * fields so a future exporter release does not break the import.
 *
 * @param productName the crafted product's name (matched against the master product list)
 * @param ts SCMDB acquisition timestamp as fractional Unix epoch seconds, or {@code null} if absent
 * @param receivedAt Blueprint Extractor acquisition timestamp as an ISO-8601 instant string (e.g.
 *     {@code 2026-03-26T16:49:31.050Z}), or {@code null} if absent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BlueprintExportEntryDto(
    @JsonProperty("productName") String productName,
    @JsonProperty("ts") Double ts,
    @JsonProperty("receivedAt") String receivedAt) {}
