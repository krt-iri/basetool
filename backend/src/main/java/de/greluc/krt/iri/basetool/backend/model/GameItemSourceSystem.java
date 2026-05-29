package de.greluc.krt.iri.basetool.backend.model;

/**
 * Tracks which external catalogues have written to a {@link GameItem} or {@link ShipType} row.
 *
 * <p>R2 only emits {@link #UEX_ONLY} (the UEX item sync is the first writer). R3+ flips {@code
 * UEX_ONLY → BOTH} when the Wiki commodity / item sync finds a match by {@code external_uuid}; the
 * Wiki sync also creates fresh {@link #WIKI_ONLY} rows for items UEX does not carry (variant skins,
 * paints UEX skipped). See SC_WIKI_SYNC_PLAN.md §6.3.1 / §6.5 for the full transition table.
 *
 * <p>Distinct from {@link MaterialSourceSystem}: that enum carries an additional {@link
 * MaterialSourceSystem#MANUAL} value for admin-created commodity rows, which has no analogue in the
 * item / vehicle domain (those rows are external-catalogue-only).
 */
public enum GameItemSourceSystem {

  /** Only the UEX sync has written to this row. R2 default. */
  UEX_ONLY,

  /** Only the SC Wiki sync has written to this row. Reached in R3+ for Wiki-only variants. */
  WIKI_ONLY,

  /** Both syncs have written to this row; the canonical fields use the §6.3.3 tie-breaker. */
  BOTH
}
