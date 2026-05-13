package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Frequency Type payload. */
public record FrequencyTypeDto(
    UUID id, String name, String description, boolean active, Integer sortIndex, Long version) {}
