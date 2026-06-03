package de.greluc.krt.iri.basetool.backend.model;

/**
 * Identifies which external catalogue a {@link BlueprintExternalAlias} row maps onto. The unique
 * constraint on {@code (source_system, external_name)} keeps the alias namespace per-source,
 * leaving room for further sources should other blueprint exporters be added later.
 */
public enum BlueprintExternalAliasSource {

  /**
   * Blueprint product name as it appears in a Star Citizen {@code Game.log} {@code "Received
   * Blueprint"} notification. Both supported exporters — the SCMDB log-watcher and the <a
   * href="https://github.com/krt-iri/basetool-bp-extractor">Basetool Blueprint Extractor</a> — read
   * that same line and therefore emit identical names, so they share this one alias namespace
   * (resolving a name once benefits imports from either tool). The personal-blueprint import (#327)
   * consults this set after a normalized exact match against the master product list fails, before
   * falling back to fuzzy suggestions.
   */
  SCMDB
}
