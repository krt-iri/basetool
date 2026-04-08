package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MissionFrequencyDto(
        UUID id,
        UUID frequencyTypeId,
        String name,
        BigDecimal value,
        Long version
) {
}
