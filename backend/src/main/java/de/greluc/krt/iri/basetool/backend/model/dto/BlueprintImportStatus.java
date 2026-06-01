package de.greluc.krt.iri.basetool.backend.model.dto;

/**
 * Per-name resolution outcome produced by the SCMDB blueprint import preview (#327, Phase 4).
 * Drives how the frontend renders each row: auto-confirmed matches versus rows that need a manual
 * pick.
 */
public enum BlueprintImportStatus {
  /** The external name normalized to an existing product key — an unambiguous direct match. */
  MATCHED,

  /** The external name resolved through a curated {@code blueprint_external_alias} (SCMDB) row. */
  MATCHED_BY_ALIAS,

  /** No exact / alias match, but one or more fuzzy candidates were found — needs a manual pick. */
  SUGGESTED,

  /** No match and no fuzzy candidate above the threshold — the user must search manually. */
  UNMATCHED,

  /** The name resolved to a product the caller already owns — nothing to add. */
  ALREADY_OWNED
}
