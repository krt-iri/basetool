package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Per-name resolution outcome mirroring the backend {@code BlueprintImportStatus} (#327). Drives
 * how the import preview modal groups each row: auto-confirmed matches versus rows needing a manual
 * pick.
 */
public enum BlueprintImportStatus {
  /** The external name matched an existing product directly. */
  MATCHED,

  /** The external name resolved through a curated SCMDB alias. */
  MATCHED_BY_ALIAS,

  /** No exact / alias match, but fuzzy candidates were found — needs a manual pick. */
  SUGGESTED,

  /** No match and no fuzzy candidate — the user must search manually. */
  UNMATCHED,

  /** The name resolved to a product the caller already owns. */
  ALREADY_OWNED
}
