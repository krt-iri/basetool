package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Refining Method payload. */
public record RefiningMethodDto(
    UUID id,
    String name,
    String description,
    String code,
    Integer ratingYield,
    Integer ratingCost,
    Integer ratingSpeed) {}
