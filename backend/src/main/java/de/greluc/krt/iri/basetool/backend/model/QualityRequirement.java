package de.greluc.krt.iri.basetool.backend.model;

/**
 * Quality a derived material requirement of an item order must be satisfied with. Chosen by the
 * requester per material at order-creation time (defaulting from the blueprint ingredient's {@code
 * minQuality}), so the same item can be ordered with different quality demands in different orders.
 * Deliberately binary — the item-order flow does not expose arbitrary quality floors, only the
 * refining-grade threshold versus "no floor".
 */
public enum QualityRequirement {

  /** Requires refining-grade quality (700+); only inventory at or above that tier satisfies it. */
  GOOD,

  /** No quality floor ("Keine"); inventory of any quality satisfies the requirement. */
  NONE
}
