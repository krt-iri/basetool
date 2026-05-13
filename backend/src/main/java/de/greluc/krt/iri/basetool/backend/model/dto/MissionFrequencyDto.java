package de.greluc.krt.iri.basetool.backend.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Data transfer record carrying Mission Frequency payload. */
public record MissionFrequencyDto(
    UUID id, FrequencyTypeDto frequencyType, BigDecimal value, Long version) {}
