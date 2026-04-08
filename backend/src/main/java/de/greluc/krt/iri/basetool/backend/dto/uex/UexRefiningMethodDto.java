package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UexRefiningMethodDto(
        Integer id,
        String name,
        String code,
        @JsonProperty("rating_yield") Integer ratingYield,
        @JsonProperty("rating_cost") Integer ratingCost,
        @JsonProperty("rating_speed") Integer ratingSpeed
) {
}