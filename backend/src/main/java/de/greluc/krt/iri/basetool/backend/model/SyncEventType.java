package de.greluc.krt.iri.basetool.backend.model;

/**
 * Catalogue of findings a sync run can record into {@link ExternalSyncReport}, per
 * SC_WIKI_SYNC_PLAN.md §8.8.
 *
 * <p>R3 (Wiki commodity merge) emits {@link #SKIP_JUNK}, {@link #CREATED_WIKI_ONLY}, {@link
 * #LOOKS_LIKE_ITEM}, {@link #LINKED_VIA_ALIAS} and {@link #MULTI_MATCH_AMBIGUOUS}. The remaining
 * values are reserved for later phases (R4 blueprint / item sync, R7 prices) and are declared now
 * so the enum — and the {@code event_type} column it maps to — is stable across the rollout.
 */
public enum SyncEventType {

  /** Wiki commodity sync: a row was dropped by the §8.9 hard-junk name filter. */
  SKIP_JUNK,

  /**
   * Wiki commodity sync: no UEX match found; a new {@code WIKI_ONLY} row was inserted invisible.
   */
  CREATED_WIKI_ONLY,

  /** UEX item sync (R2+): an item had no Wiki cross-reference. */
  CREATED_UEX_ONLY,

  /**
   * Wiki commodity sync: the row's name matches the §4.3 "items masquerading as commodities" set
   * (Ace Interceptor Helmet, MedGel, …). Inserted invisible so an admin can decide.
   */
  LOOKS_LIKE_ITEM,

  /** Wiki commodity sync: the row was linked to a local material via the alias table. */
  LINKED_VIA_ALIAS,

  /** Wiki item sync (R4): joined an existing UEX row by shared {@code external_uuid}. */
  LINKED_VIA_UUID,

  /** Wiki commodity sync: the canonical name hit more than one UEX row; no link made. */
  MULTI_MATCH_AMBIGUOUS,

  /** Wiki blueprint sync (R4): an ingredient resource / item could not be resolved. */
  UNRESOLVED_INGREDIENT,

  /** Both sides (R4+): UEX and Wiki disagree on the manufacturer for the same UUID. */
  MANUFACTURER_MISMATCH,

  /** Both sides: the optimistic-lock retry budget for a joint row was exhausted. */
  SYNC_RACE_CONFLICT,

  /** Wiki item sync (R4): a UUID present in UEX is absent on the Wiki. */
  WIKI_MISSING,

  /** Vehicle backfill: a {@code ship_type.name} matched more than one UEX vehicle. */
  BACKFILL_AMBIGUOUS
}
