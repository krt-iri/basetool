package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

public record MaterialCategoryDto(
        UUID id,
        String name,
        Long version
) {}
