package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A crafting blueprint a single user (identified by the Keycloak {@code sub} claim, stored in
 * {@link #ownerSub}) has unlocked in-game. Part of the Personal Inventory area (#327), alongside
 * {@link PersonalInventoryItem}.
 *
 * <p>Ownership is modelled <strong>per product</strong>, not per recipe: several SC Wiki blueprint
 * recipes can share one product name, and the SCMDB import only knows the product name, so a single
 * row stands for "I own the blueprint for product X". Identity is the normalized {@link
 * #productKey} (derived from the SC Wiki output name); {@link #productName} keeps the original
 * display spelling and {@link #outputItem} optionally links the resolved {@link GameItem} for later
 * cross-feature use — it is informational, not the identity.
 *
 * <p>The unique constraint on {@code (owner_sub, product_key)} guarantees a user owns each product
 * at most once. Optimistic locking is inherited via {@link AbstractEntity#getVersion()}.
 */
@Entity
@Table(
    name = "personal_blueprint",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_personal_blueprint_owner_product",
            columnNames = {"owner_sub", "product_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalBlueprint extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** Keycloak JWT {@code sub} of the owning user. Never expose to clients. */
  @Column(name = "owner_sub", nullable = false, length = 64)
  private String ownerSub;

  /**
   * Normalized product identity (lowercased, collapsed whitespace, normalized punctuation of the SC
   * Wiki output name). Unique per owner; the import and search both match on this key.
   */
  @Column(name = "product_key", nullable = false, length = 255)
  private String productKey;

  /** Original display spelling of the product at the time of save. */
  @Column(name = "product_name", nullable = false, length = 255)
  private String productName;

  /**
   * Optional link to the resolved produced item. {@code null} when the product is not (yet) present
   * in {@code game_item}; informational only, never the ownership identity.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "output_item_id")
  private GameItem outputItem;

  /** Optional in-game acquisition time; pre-filled from the export timestamp on import. */
  @Column(name = "acquired_at")
  private Instant acquiredAt;

  /** Optional free-form note the owner attaches to the entry. */
  @Column(length = 2000)
  private String note;
}
