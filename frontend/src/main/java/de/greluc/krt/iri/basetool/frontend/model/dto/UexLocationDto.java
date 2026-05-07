package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Combined typeahead entry for cities and space stations. Mirrors the backend
 * {@code UexLocationDto}.
 */
public record UexLocationDto(
        Integer uexId,
        PersonalInventoryLocationType type,
        String name,
        String starSystemName,
        String parentName
) {}
