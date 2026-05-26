package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Frontend mirror of the backend {@code OrgUnitKind} enum — discriminator for the two tenant
 * subtypes carried by the {@code org_unit} table (Squadron and Spezialkommando). The frontend uses
 * the enum to branch on the membership rendering (Staffel vs SK chip variant) and to filter
 * client-side without a round-trip back to the backend.
 *
 * <p>The string values must match the backend enum's {@code name()} output verbatim so JSON
 * deserialisation of {@code OrgUnitMembershipDto} resolves the {@code kind} field cleanly.
 */
public enum OrgUnitKind {
  /** Staffel — the original tenant kind that has driven the multi-tenancy work since Phase 1. */
  SQUADRON,

  /** Spezialkommando — the second tenant kind introduced by the Spezialkommando R2.a slice. */
  SPECIAL_COMMAND
}
