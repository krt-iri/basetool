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

import java.util.UUID;

/**
 * One ranked master-data candidate offered for a raw screen name that the refinery screenshot
 * import could not (or only fuzzily) match (#434, plan §7.5). The review UI lists these highest
 * score first so the user assigns the intended material with one click instead of searching the
 * full catalogue — the refinery counterpart of {@link BlueprintImportSuggestionDto}.
 *
 * @param id id of the candidate {@code Material} (echoed back when the user picks it)
 * @param name display name of the candidate, e.g. {@code "Stileron (Raw)"}
 * @param score similarity to the raw screen name in {@code [0.0, 1.0]} (1.0 = identical after
 *     canonical folding)
 */
public record ImportSuggestionDto(UUID id, String name, double score) {}
