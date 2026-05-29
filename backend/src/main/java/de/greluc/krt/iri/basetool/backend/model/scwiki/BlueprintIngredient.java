package de.greluc.krt.iri.basetool.backend.model.scwiki;

import de.greluc.krt.iri.basetool.backend.model.AbstractEntity;
import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.Material;
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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One ingredient line of a {@link Blueprint} (SC_WIKI_SYNC_PLAN.md §6.3.3). A line is either a
 * {@link BlueprintIngredientKind#RESOURCE} (consumes a {@link Material}, quantity in SCU) or a
 * {@link BlueprintIngredientKind#ITEM} (consumes a {@link GameItem}, quantity in whole units).
 *
 * <p>The resolved FK ({@link #material} / {@link #gameItem}) may be {@code null} when the sync
 * could not resolve the Wiki reference yet — the raw {@link #wikiResourceUuid} / {@link
 * #wikiItemUuid} / {@link #wikiNameSnapshot} are always persisted so a later sync (after an admin
 * adds an alias, or after the item lands in {@code game_item}) re-resolves transparently without
 * re-fetching the Wiki (§8.2). The DB CHECK constraints enforce kind/FK and kind/quantity
 * exclusivity but permit a null matching FK while unresolved (see V114 migration header).
 */
@Entity
@Table(name = "blueprint_ingredient")
@Getter
@Setter
@ToString(exclude = {"blueprint", "material", "gameItem"})
@NoArgsConstructor
public class BlueprintIngredient extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** Owning blueprint. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "blueprint_id", nullable = false)
  private Blueprint blueprint;

  /** Position within the blueprint's ingredient list (drives {@code @OrderBy}). */
  @Column(name = "order_index", nullable = false)
  private Integer orderIndex;

  /** Whether this line is a RESOURCE (commodity) or an ITEM (game item). */
  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 16)
  private BlueprintIngredientKind kind;

  /** Resolved commodity for a RESOURCE line; {@code null} when unresolved or for an ITEM line. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "material_id")
  private Material material;

  /** Resolved game item for an ITEM line; {@code null} when unresolved or for a RESOURCE line. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "game_item_id")
  private GameItem gameItem;

  /** Raw Wiki resource UUID for a RESOURCE line — kept for forensic re-resolution. */
  @Column(name = "wiki_resource_uuid")
  private UUID wikiResourceUuid;

  /** Raw Wiki item UUID for an ITEM line — kept for forensic re-resolution. */
  @Column(name = "wiki_item_uuid")
  private UUID wikiItemUuid;

  /** Wiki display name of the ingredient at sync time — kept even when the FK resolves. */
  @Column(name = "wiki_name_snapshot")
  private String wikiNameSnapshot;

  /** Quantity in SCU for a RESOURCE line. */
  @Column(name = "quantity_scu")
  private Double quantityScu;

  /** Quantity in whole units for an ITEM line. */
  @Column(name = "quantity_units")
  private Integer quantityUnits;
}
