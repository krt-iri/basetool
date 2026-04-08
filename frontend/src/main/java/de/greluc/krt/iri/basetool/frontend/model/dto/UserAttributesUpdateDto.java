package de.greluc.krt.iri.basetool.frontend.model.dto;

public record UserAttributesUpdateDto(
        Integer rank,
        String description,
        String displayName,
        Long version
) {}
