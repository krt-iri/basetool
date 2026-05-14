package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound JSON record for UEX Corp's refining-method endpoint. Mapped to the project's own {@code
 * RefiningMethod} entity by {@code UexRefinerySyncService}.
 */
public record UexRefiningMethodDto(
    Integer id,
    String name,
    String code,
    @JsonProperty("rating_yield") Integer ratingYield,
    @JsonProperty("rating_cost") Integer ratingCost,
    @JsonProperty("rating_speed") Integer ratingSpeed) {}
