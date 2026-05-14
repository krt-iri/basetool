package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PersonalInventoryLocationType;

/**
 * Combined search result entry exposing UEX cities and space stations to the frontend for the
 * personal-inventory location typeahead. The numeric {@code uexId} is the value persisted in {@code
 * personal_inventory_item.location_uex_id} and must be paired with {@link #type()} to identify the
 * location unambiguously.
 */
public record UexLocationDto(
    Integer uexId,
    PersonalInventoryLocationType type,
    String name,
    String starSystemName,
    String parentName) {}
