package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.QuantityType;
import java.util.UUID;

public record MaterialReferenceDto(UUID id, String name, QuantityType quantityType) {}
