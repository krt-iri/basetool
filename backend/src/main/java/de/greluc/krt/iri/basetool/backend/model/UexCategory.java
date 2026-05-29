package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Reference row for UEX Corp's {@code /categories} endpoint (V109 / R1, populated in R2).
 *
 * <p>The 98+ rows of the UEX category table drive {@code UexItemSyncService}'s walk through {@code
 * /items?id_category=<n>} and carry the {@link #getSection()} + {@link #getName()} pair used by the
 * kind-derivation table in SC_WIKI_SYNC_PLAN.md §6.3.1. Section "Armor" maps to {@link
 * GameItemKind#ARMOR}, "Vehicle Weapons" to {@link GameItemKind#VEHICLE_WEAPON}, and so on.
 *
 * <p>The PK is UEX's integer id (1..98+) rather than a synthetic UUID — the id is stable across
 * runs (UEX never re-numbers categories), survives JOIN queries from {@code game_item} cheaply, and
 * matches the on-the-wire identifier the sync uses.
 */
@Entity
@Table(name = "uex_category")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UexCategory extends AbstractEntity<Integer> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  private Integer id;

  /** {@code "item"} or {@code "vehicle"} — drives the kind derivation in the sync. */
  @Column(nullable = false, length = 16)
  private String type;

  /**
   * Coarse grouping displayed in UEX's UI (e.g. {@code "Armor"}, {@code "Vehicle Weapons"}, {@code
   * "Systems"}). Indexed on the DB side; consumed by the item-sync kind derivation.
   */
  @Column(nullable = false, length = 64)
  private String section;

  /** Subcategory display name (e.g. {@code "Helmets"}, {@code "Torso"}, {@code "Coolers"}). */
  @Column(nullable = false, length = 128)
  private String name;

  /**
   * UEX's {@code is_game_related} flag. Categories with this flag false are reference-only and
   * should NOT drive an item walk (e.g. internal taxonomy buckets).
   */
  @Column(name = "is_game_related", nullable = false)
  private Boolean isGameRelated;

  /** UEX's {@code is_mining} flag. Used by mining-specific UI filters; not consumed by R2. */
  @Column(name = "is_mining", nullable = false)
  private Boolean isMining;

  /** Timestamp of the most recent successful UEX sync touch. */
  @Column(name = "uex_synced_at")
  private Instant uexSyncedAt;

  /**
   * Timestamp of the first sync run in which UEX no longer returned this category. Soft-delete
   * marker; cleared on the next sync that sees it again.
   */
  @Column(name = "uex_deleted_at")
  private Instant uexDeletedAt;
}
