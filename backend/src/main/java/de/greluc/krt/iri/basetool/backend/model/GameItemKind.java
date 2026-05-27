package de.greluc.krt.iri.basetool.backend.model;

/**
 * Classification of a {@link GameItem} row by the kind of in-game asset it represents.
 *
 * <p>UEX derives the kind from the row's {@link UexCategory#getSection() section} per the table in
 * SC_WIKI_SYNC_PLAN.md §6.3.1 (Armor → {@link #ARMOR}, "Vehicle Weapons" → {@link #VEHICLE_WEAPON},
 * Systems / Utility / Avionics / Propulsion → {@link #VEHICLE_ITEM}, …). Wiki derives it from the
 * {@code classification} string (e.g. {@code "FPS.Armor.Helmet"} → ARMOR; {@code "Ship.Weapon"} →
 * VEHICLE_WEAPON). When UEX and Wiki disagree, the more-specific value wins ({@code
 * WEAPON_ATTACHMENT > WEAPON > VEHICLE_WEAPON > VEHICLE_ITEM > GENERIC}).
 */
public enum GameItemKind {

  /** Default; used for rows whose section / classification didn't match a more specific kind. */
  GENERIC,

  /** Vehicle-bound component (cooler, shield, power plant, jump drive, …). */
  VEHICLE_ITEM,

  /** Mounted vehicle weapon. */
  VEHICLE_WEAPON,

  /** Hand-held FPS weapon (rifle, sidearm, …). */
  WEAPON,

  /** Hand-weapon attachment (scope, magazine, barrel mod). */
  WEAPON_ATTACHMENT,

  /** FPS armor piece (helmet, torso, arms, legs, backpack). */
  ARMOR,

  /** Clothing / cosmetic / under-suit. */
  CLOTHING,

  /** Food or drink item. */
  FOOD
}
