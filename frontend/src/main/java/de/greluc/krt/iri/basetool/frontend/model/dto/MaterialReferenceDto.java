package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Material Reference payload. */
public record MaterialReferenceDto(UUID id, String name, String quantityType) {}
