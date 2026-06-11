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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data transfer record carrying Mission Unit payload. {@code responsibleUser} is the explicit
 * responsible person (PII-free reference tuple); when {@code null} the view falls back to the
 * assigned ship's owner. {@code note} is the unit's free-text planning note.
 */
public record MissionUnitDto(
    UUID id,
    String name,
    ShipTypeDto shipType,
    ShipDto ship,
    Double frequency,
    Boolean highValueUnit,
    UserReferenceDto responsibleUser,
    String note,
    List<MissionCrewDto> crew) {
  /**
   * Aggregates this unit's crew job assignments into a name-to-count map preserving first-seen
   * order, used by the Mission detail view's "Job summary" widget.
   */
  public Map<String, Integer> getJobSummary() {
    if (crew == null) {
      return Map.of();
    }
    Map<String, Integer> summary = new LinkedHashMap<>();
    for (MissionCrewDto c : crew) {
      if (c.jobTypes() != null) {
        for (JobTypeDto job : c.jobTypes()) {
          String jobName = job.name();
          if (jobName != null) {
            summary.put(jobName, summary.getOrDefault(jobName, 0) + 1);
          }
        }
      }
    }
    return summary;
  }
}
