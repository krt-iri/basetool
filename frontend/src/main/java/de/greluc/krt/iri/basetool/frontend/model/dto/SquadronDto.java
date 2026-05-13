package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Squadron payload. */
public record SquadronDto(
    UUID id, String name, String shorthand, String description, Boolean active, Long version) {}
