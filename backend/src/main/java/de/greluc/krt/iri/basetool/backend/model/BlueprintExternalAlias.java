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
 * Curated cross-reference row mapping an external catalogue's blueprint name onto a local product.
 *
 * <p>The SCMDB log-watcher export carries only a blueprint {@code productName} string, while the
 * local master list (table {@code blueprint}, synced from the SC Wiki) keys products by output
 * name. Names drift between the two sources, so the personal-blueprint import (#327) consults this
 * table as its alias-resolution step: after a normalized exact match against the master product
 * list fails, it looks up the {@code (source_system, external_name)} pair here and dereferences
 * {@link #productKey} to the product. When a user/admin resolves an unmatched name manually, that
 * decision is persisted here so future imports auto-match. Mirrors the {@link
 * MaterialExternalAlias} pattern.
 *
 * <p>The alias points at a product by its normalized {@link #productKey} (plus a {@link
 * #productName} snapshot and the optional resolved {@link #outputItem}) rather than at a single
 * recipe row, because ownership is per product. The unique constraint on {@code (source_system,
 * external_name)} guarantees an external name resolves deterministically — a duplicate add returns
 * HTTP 409.
 */
@Entity
@Table(
    name = "blueprint_external_alias",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_blueprint_external_alias_source_external_name",
            columnNames = {"source_system", "external_name"}))
@Getter
@Setter
@ToString(exclude = "outputItem")
@NoArgsConstructor
@AllArgsConstructor
public class BlueprintExternalAlias extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** External catalogue the alias belongs to (currently {@code SCMDB}). */
  @Enumerated(EnumType.STRING)
  @Column(name = "source_system", nullable = false, length = 32)
  private BlueprintExternalAliasSource sourceSystem;

  /**
   * Blueprint product name as it appears in the external catalogue (the SCMDB export {@code
   * productName}).
   */
  @Column(name = "external_name", nullable = false, length = 255)
  private String externalName;

  /**
   * Normalized product key this external name resolves to (matches {@link
   * PersonalBlueprint#getProductKey()}).
   */
  @Column(name = "product_key", nullable = false, length = 255)
  private String productKey;

  /** Display spelling of the resolved product at the time the alias was created. */
  @Column(name = "product_name", nullable = false, length = 255)
  private String productName;

  /**
   * Optional link to the resolved produced item ({@code null} when the product is not present in
   * {@code game_item}). Audit aid only; the resolution dereferences {@link #productKey}.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "output_item_id")
  private GameItem outputItem;

  /** Free-form note explaining the alias provenance (e.g. who verified the in-game match). */
  @Column(columnDefinition = "TEXT")
  private String note;

  /**
   * Identifier of the alias creator: {@code "system"} for seeded rows, the JWT {@code sub} for
   * user/admin-created rows.
   */
  @Column(name = "created_by", length = 255)
  private String createdBy;
}
