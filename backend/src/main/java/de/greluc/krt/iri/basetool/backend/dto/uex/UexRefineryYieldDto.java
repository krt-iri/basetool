package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound JSON record for UEX Corp's refinery-yield endpoint. Maps a refining method to the
 * commodity output at a given refinery terminal; consumed by {@code UexRefinerySyncService}.
 */
public record UexRefineryYieldDto(
    Integer id,
    @JsonProperty("id_commodity") Integer idCommodity,
    @JsonProperty("id_terminal") Integer idTerminal,
    Integer value) {}
