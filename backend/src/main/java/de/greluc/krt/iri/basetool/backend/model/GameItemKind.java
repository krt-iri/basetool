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
  FOOD;

  /**
   * Merges an existing kind with an incoming one per the SC_WIKI_SYNC_PLAN.md §6.3.1
   * "more-specific-wins, never downgrade to {@link #GENERIC}" rule. This is the single shared
   * arbiter applied identically whether UEX or Wiki was the last writer (§6.3.1: "applies whether
   * UEX or Wiki was the last writer") — so a paint one source files as {@link #VEHICLE_ITEM} is
   * never downgraded to {@link #GENERIC} when the other re-catalogues it under a generic section.
   *
   * <p>Semantics: a {@code null} or {@link #GENERIC} {@code existing} yields to {@code incoming};
   * an incoming {@link #GENERIC} (or an equal kind) keeps {@code existing}; {@link
   * #WEAPON_ATTACHMENT} refines {@link #WEAPON} and {@link #VEHICLE_WEAPON} refines {@link
   * #VEHICLE_ITEM}; any other pair of distinct specific kinds keeps {@code existing} (no
   * cross-family flip).
   *
   * @param existing the kind currently on the row (may be {@code null})
   * @param incoming the kind derived from the current sync pass
   * @return the kind to persist
   */
  public static GameItemKind mergeMoreSpecific(GameItemKind existing, GameItemKind incoming) {
    if (existing == null || existing == GENERIC) {
      return incoming;
    }
    if (incoming == GENERIC || existing == incoming) {
      return existing;
    }
    if (existing == WEAPON && incoming == WEAPON_ATTACHMENT) {
      return incoming;
    }
    if (existing == VEHICLE_ITEM && incoming == VEHICLE_WEAPON) {
      return incoming;
    }
    return existing;
  }
}
