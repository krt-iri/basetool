package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Mirror of the backend enum used to disambiguate UEX location entries between cities and
 * space stations. Kept as a lightweight frontend copy to avoid a hard module dependency
 * on the backend module while still ensuring type safety on REST payloads.
 */
public enum PersonalInventoryLocationType {
    CITY,
    SPACE_STATION
}
