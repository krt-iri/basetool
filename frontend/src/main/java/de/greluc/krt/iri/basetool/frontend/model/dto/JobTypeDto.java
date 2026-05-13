package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

public record JobTypeDto(
    UUID id,
    String name,
    String description,
    String archetype,
    UUID parentId,
    Boolean active,
    Boolean isLeadershipRole,
    Long version) {}
