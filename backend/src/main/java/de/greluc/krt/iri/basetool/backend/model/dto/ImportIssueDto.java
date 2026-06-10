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

import java.util.List;

/**
 * One review finding on a refinery screenshot import draft (#434, plan §7.5). The frontend anchors
 * the finding to a form field via {@link #field} and colours it via {@link #severity}; {@link
 * #code} selects the translated message ({@code refineryImport.issue.*}).
 *
 * <p>{@code field} addressing convention: issues on a row that exists in the draft use the index
 * within {@code draft.order().goods()} plus the sub-field, e.g. {@code "goods[2].inputMaterial"} /
 * {@code "goods[2].quality"}. Issues on rows that were <em>not</em> added to the draft (skips,
 * un-quoted rows) use the bare on-screen row reference {@code "goods[<rowIndex>]"} — there is no
 * draft field to anchor them to, so the review UI lists them in the summary only. Order-level
 * issues use the plain field name ({@code "location"}, {@code "refiningMethod"}, {@code "quoted"},
 * {@code "orders"}, {@code "rawInManifestTotal"}, {@code "rawToRefineTotal"}).
 *
 * @param field dotted path per the convention above; never {@code null}
 * @param rawValue the verbatim screen read (or a compact diagnostic like {@code "32295 != 31000"}
 *     for checksum issues) so the user sees what the extractor produced
 * @param code machine-readable reason; the frontend translates it
 * @param severity visual grading (danger / warning / info)
 * @param confidence contextual confidence in {@code [0,1]}: the fuzzy match score for {@link
 *     ImportIssueCode#LOW_CONFIDENCE_MATERIAL}, otherwise the row's derived read confidence from
 *     the contract; {@code null} when neither applies (order-level issues)
 * @param suggestions ranked master-data candidates for {@code UNMATCHED_MATERIAL} / {@code
 *     LOW_CONFIDENCE_MATERIAL} (highest score first); {@code null} for every other code
 */
public record ImportIssueDto(
    String field,
    String rawValue,
    ImportIssueCode code,
    ImportIssueSeverity severity,
    Double confidence,
    List<ImportSuggestionDto> suggestions) {}
