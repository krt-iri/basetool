package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Material Category payload. */
public record MaterialCategoryDto(UUID id, String name, Long version) {}
