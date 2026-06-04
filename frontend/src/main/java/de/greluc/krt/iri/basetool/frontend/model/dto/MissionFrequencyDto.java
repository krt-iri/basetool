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

import java.math.BigDecimal;
import java.util.UUID;

/** Data transfer record carrying Mission Frequency payload. */
public record MissionFrequencyDto(
    UUID id, FrequencyTypeRef frequencyType, BigDecimal value, Long version) {
  /** Immutable record carrying Frequency Type Ref data. */
  public record FrequencyTypeRef(UUID id, String name) {}

  /** Convenience accessor returning the nested {@code frequencyType.id()}, or {@code null}. */
  public UUID frequencyTypeId() {
    return frequencyType != null ? frequencyType.id() : null;
  }
}
