package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UexRefineryYieldDto(
    Integer id,
    @JsonProperty("id_commodity") Integer idCommodity,
    @JsonProperty("id_terminal") Integer idTerminal,
    Integer value) {}
