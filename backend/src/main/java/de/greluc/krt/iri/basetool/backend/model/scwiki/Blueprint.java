package de.greluc.krt.iri.basetool.backend.model.scwiki;

import de.greluc.krt.iri.basetool.backend.model.AbstractEntity;
import de.greluc.krt.iri.basetool.backend.model.GameItem;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A crafting blueprint synced from the SC Wiki {@code /api/blueprints} endpoint
 * (SC_WIKI_SYNC_PLAN.md §6.3.2). Keyed by {@link #scwikiUuid}; produces an output {@link GameItem}
 * from an ordered list of {@link BlueprintIngredient}s and yields a list of {@link
 * BlueprintDismantleReturn}s when dismantled.
 *
 * <p>The blueprint owns both child collections with {@code cascade = ALL} + {@code orphanRemoval}.
 * The R4 {@code ScWikiBlueprintSyncService} re-syncs by mutating the managed collections in place
 * (reusing lines by index, dropping trailing ones) and relying on Hibernate dirty-checking — it
 * never issues a {@code @Modifying} bulk update inside the per-blueprint loop, so the CLAUDE.md
 * detach-clear trap does not apply.
 */
@Entity
@Table(name = "blueprint")
@Getter
@Setter
@ToString(exclude = {"outputItem", "ingredients", "dismantleReturns"})
@NoArgsConstructor
public class Blueprint extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** SC Wiki blueprint UUID — the cross-sync identity key. UNIQUE at the DB level. */
  @Column(name = "scwiki_uuid", nullable = false, unique = true)
  private UUID scwikiUuid;

  /** SC Wiki internal key, e.g. {@code "BP_CRAFT_AMRS_LaserCannon_S1"}. */
  @Column(name = "scwiki_key")
  private String scwikiKey;

  /**
   * The item this blueprint produces, resolved from the Wiki {@code output_item_uuid} against
   * {@code game_item.external_uuid}. {@code null} when the output item is not (yet) in {@code
   * game_item}; {@link #outputName} preserves the Wiki name regardless.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "output_item_id")
  private GameItem outputItem;

  /** Wiki display name of the output item (kept even when {@link #outputItem} is unresolved). */
  @Column(name = "output_name")
  private String outputName;

  /** Wiki category UUID for the blueprint (informational; not resolved to an entity in R4). */
  @Column(name = "category_uuid")
  private UUID categoryUuid;

  /** Craft time in seconds. */
  @Column(name = "craft_time_seconds")
  private Integer craftTimeSeconds;

  /** Whether the recipe is unlocked by default (vs. mission-gated). */
  @Column(name = "is_available_by_default", nullable = false)
  private Boolean isAvailableByDefault = false;

  /** Wiki-reported ingredient count (a cross-check against the persisted {@link #ingredients}). */
  @Column(name = "ingredient_count")
  private Integer ingredientCount;

  /** Wiki-reported count of missions that unlock this blueprint. */
  @Column(name = "unlocking_missions_count")
  private Integer unlockingMissionsCount;

  /** Game version in which the Wiki last reported this blueprint. */
  @Column(name = "game_version_seen")
  private String gameVersionSeen;

  /** Last successful SC Wiki sync touch. */
  @Column(name = "scwiki_synced_at")
  private Instant scwikiSyncedAt;

  /** Soft-delete marker: first run in which the Wiki stopped returning this blueprint. */
  @Column(name = "scwiki_deleted_at")
  private Instant scwikiDeletedAt;

  /** Ordered ingredient lines (RESOURCE or ITEM). Owned with cascade + orphan removal. */
  @OneToMany(
      mappedBy = "blueprint",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("orderIndex ASC")
  private List<BlueprintIngredient> ingredients = new ArrayList<>();

  /** Ordered dismantle-return lines (RESOURCE only). Owned with cascade + orphan removal. */
  @OneToMany(
      mappedBy = "blueprint",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("orderIndex ASC")
  private List<BlueprintDismantleReturn> dismantleReturns = new ArrayList<>();
}
