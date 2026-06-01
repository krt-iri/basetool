package de.greluc.krt.iri.basetool.backend.model;

/**
 * Discriminates the two kinds of {@link JobOrder}. A {@link #MATERIAL} order lists raw materials to
 * procure and deliver (the legacy behaviour); an {@link #ITEM} order lists finished items to
 * produce, from which the required materials are derived and aggregated via blueprint data.
 * Existing rows are backfilled to {@link #MATERIAL} by migration V123, so the discriminator never
 * widens the behaviour of historical orders.
 */
public enum JobOrderType {

  /** Order requesting raw materials directly (commodity + quantity + minimum quality). */
  MATERIAL,

  /** Order requesting finished items; needed materials are derived from each item's blueprint. */
  ITEM
}
