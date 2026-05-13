package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Data transfer record carrying Mission Unit payload. */
public record MissionUnitDto(
    UUID id,
    String name,
    ShipTypeDto shipType,
    ShipDto ship,
    Double frequency,
    Boolean highValueUnit,
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
