package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend's {@code SquadronReferenceDto}. Carries the squadron id, name and
 * short handle the templates render in the staffel column / badge. Kept structurally identical to
 * the backend record so Jackson maps the response payload one-to-one without custom mixins.
 */
public record SquadronReferenceDto(UUID id, String name, String shorthand) {}
