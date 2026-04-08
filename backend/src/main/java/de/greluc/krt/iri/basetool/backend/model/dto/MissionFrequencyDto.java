package de.greluc.krt.iri.basetool.backend.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MissionFrequencyDto(
        UUID id,
        FrequencyTypeDto frequencyType,
        BigDecimal value,
        Long version
) {
}