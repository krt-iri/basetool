package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record MissionUnitDto(
        UUID id,
        String name,
        ShipTypeDto shipType,
        ShipDto ship,
        Double frequency,
        Boolean highValueUnit,
        Set<MissionCrewDto> crew
) {
    public Map<String, Integer> getJobSummary() {
        if (crew == null) return Map.of();
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
