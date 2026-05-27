package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Curated cross-reference row mapping an external catalogue's commodity name onto a local {@link
 * Material}.
 *
 * <p>The R3 SC Wiki commodity sync (and a future R6 UEX-side counterpart) consult this table as the
 * alias-resolution layer in their {@code findMaterialFor(dto)} chain: after a direct UUID match
 * fails, the sync looks up the {@code (source_system, external_name)} pair here and dereferences
 * {@link #material} to the local row. Without this table the only fallback would be a fuzzy name
 * match — see SC_WIKI_SYNC_PLAN.md §8.1.1 for the full resolution order.
 *
 * <p>R1 ships the V108 seed entries (4 fuzzy + 2 manual aliases verified on 2026-05-27) plus this
 * entity, repository, service and the {@code /admin/material-aliases} CRUD page. Aliases not in the
 * V108 seed (Construction-* triplet, Combat Supplies) are intentionally created manually by an
 * admin after in-game grade verification.
 *
 * <p>The unique constraint on {@code (source_system, external_name)} guarantees that an external
 * name resolves deterministically — adding a duplicate via the admin form returns HTTP 409.
 */
@Entity
@Table(
    name = "material_external_alias",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_material_external_alias_source_external_name",
            columnNames = {"source_system", "external_name"}))
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MaterialExternalAlias extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** The local material this alias points at. Cannot be {@code null} — the FK enforces this. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "material_id", nullable = false)
  private Material material;

  /** External catalogue the alias belongs to ({@code UEX} or {@code SCWIKI}). */
  @Enumerated(EnumType.STRING)
  @Column(name = "source_system", nullable = false, length = 32)
  private MaterialExternalAliasSource sourceSystem;

  /** Commodity name as it appears in the external catalogue (e.g. Wiki's {@code "Raw Silicon"}). */
  @Column(name = "external_name", nullable = false)
  private String externalName;

  /**
   * Optional external internal key (e.g. SC Wiki's {@code "Agricium"} key field). Audit aid; the
   * resolution chain does not consult it.
   */
  @Column(name = "external_key")
  private String externalKey;

  /**
   * Optional external UUID. SC Wiki carries one for every commodity; UEX exposes integer ids
   * instead and leaves this {@code null} for UEX-side aliases.
   */
  @Column(name = "external_uuid")
  private UUID externalUuid;

  /**
   * Optional external short code (e.g. UEX's 4-letter trading codes like {@code "AGRI"}). Audit aid
   * only; the unique constraint stays on {@code (source_system, external_name)}.
   */
  @Column(name = "external_code", length = 64)
  private String externalCode;

  /** Free-form note explaining the alias provenance (verification source, in-game observation). */
  @Column(columnDefinition = "TEXT")
  private String note;

  /**
   * Identifier of the alias creator: {@code "system"} for V108 seed rows, the JWT {@code sub} for
   * admin-created rows.
   */
  @Column(name = "created_by")
  private String createdBy;
}
