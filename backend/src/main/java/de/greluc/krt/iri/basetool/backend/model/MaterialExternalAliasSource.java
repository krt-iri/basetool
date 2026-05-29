package de.greluc.krt.iri.basetool.backend.model;

/**
 * Identifies which external catalogue a {@link MaterialExternalAlias} row maps onto. The same local
 * {@link Material} can carry one UEX alias and one SC Wiki alias side-by-side; the unique
 * constraint on {@code (source_system, external_name)} keeps the namespace per-source.
 */
public enum MaterialExternalAliasSource {

  /** UEX (uexcorp.space) commodity name. Used by the R6+ UEX commodity sync as a fallback match. */
  UEX,

  /**
   * SC Wiki (api.star-citizen.wiki) commodity name. The R3 Wiki commodity sync consults this set
   * after a direct UUID match fails — see SC_WIKI_SYNC_PLAN.md §8.1.1 step 2.
   */
  SCWIKI
}
