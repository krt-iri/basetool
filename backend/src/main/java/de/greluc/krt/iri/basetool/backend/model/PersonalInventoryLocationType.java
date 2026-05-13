package de.greluc.krt.iri.basetool.backend.model;

/**
 * Discriminator for the UEX location referenced by a {@link PersonalInventoryItem}. The numeric UEX
 * id alone is not unique across location categories, so the type is persisted alongside it.
 */
public enum PersonalInventoryLocationType {
  CITY,
  SPACE_STATION
}
