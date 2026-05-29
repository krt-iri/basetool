package de.greluc.krt.iri.basetool.backend.model.scwiki;

/**
 * Discriminator for a {@link BlueprintIngredient}: whether the line consumes a bulk commodity or a
 * discrete game item (SC_WIKI_SYNC_PLAN.md §3.3 / §6.3.3).
 *
 * <p>The Wiki blueprint payload carries a {@code kind} field per ingredient: {@code "resource"}
 * references a commodity by {@code resource_type_uuid} (quantity in SCU); {@code "item"} references
 * a game item by {@code item_uuid} (quantity in whole units).
 */
public enum BlueprintIngredientKind {

  /** A bulk commodity ingredient — resolves to a {@code material}, quantity in SCU. */
  RESOURCE,

  /** A discrete game-item ingredient — resolves to a {@code game_item}, quantity in units. */
  ITEM
}
