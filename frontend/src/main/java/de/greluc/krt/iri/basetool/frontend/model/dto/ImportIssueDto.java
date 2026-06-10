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

package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Frontend mirror of the backend {@code ImportIssueDto} (#434/#435): one review finding on a
 * refinery screenshot import draft. {@code field} follows the backend's addressing convention —
 * draft-row issues use {@code "goods[<draftIndex>].<subField>"} (anchored to the rendered form
 * row), skipped-row issues use the bare {@code "goods[<rowIndex>]"} (summary only), order-level
 * issues use plain names ({@code "location"}, {@code "refiningMethod"}, {@code "quoted"}, …).
 * Fields must stay in lockstep with the backend record (mirror-DTO rule).
 *
 * @param field dotted path per the convention above
 * @param rawValue verbatim screen read or compact diagnostic
 * @param code machine-readable reason, translated via {@code refineryImport.issue.<CODE>}
 * @param severity visual grading
 * @param confidence contextual confidence in {@code [0,1]}; fuzzy score for {@code
 *     LOW_CONFIDENCE_MATERIAL}, otherwise the row's derived read confidence; nullable
 * @param suggestions ranked candidates for material issues; {@code null} otherwise
 */
public record ImportIssueDto(
    String field,
    String rawValue,
    ImportIssueCode code,
    ImportIssueSeverity severity,
    Double confidence,
    List<ImportSuggestionDto> suggestions) {}
