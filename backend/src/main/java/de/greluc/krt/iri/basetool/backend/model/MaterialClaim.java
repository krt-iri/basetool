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
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A squadron's <em>claim</em> ("Eintragung") for a partial quantity of one material bucket on a
 * public Spezialkommando job order — the way profit squadrons sign up to deliver part of an SK
 * order's material requirement (Job-Order rework #340, Phase 4 / #344).
 *
 * <p>The claim is keyed on the aggregated bucket {@code (jobOrder, material, qualityRequirement)}
 * so it works uniformly for both order kinds: a {@code MATERIAL} order's required amount per bucket
 * is Σ {@link JobOrderMaterial#getAmount()} (with the bucket derived from {@code minQuality}), an
 * {@code ITEM} order's is Σ {@link JobOrderItemMaterial#getRequiredQuantity()}. A partial-unique
 * constraint enforces <b>one claim per {@code (bucket, claimingOrgUnit)}</b> — a squadron raising
 * its stake updates its existing row rather than inserting a duplicate.
 *
 * <p>Claims are <b>signal-only</b>: they record intent, never move inventory. They live as an
 * independent aggregate (no mapped collection on {@link JobOrder}) so mutating a claim never bumps
 * the parent order's {@code @Version}; the reconciliation hooks in {@code JobOrderService} delete
 * them through {@code MaterialClaimRepository} rather than through a JPA cascade. Invariants
 * (SK-only, no overclaim Σ ≤ required, terminal-status freeze) are enforced in {@code
 * MaterialClaimService}, not here.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "material_claim")
public class MaterialClaim extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** The public SK job order this claim signs up against. */
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "job_order_id", nullable = false)
  private JobOrder jobOrder;

  /** The material the claiming squadron commits to deliver. */
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "material_id", nullable = false)
  private Material material;

  /**
   * The quality bucket this claim falls into — {@code GOOD} (700+) or {@code NONE} (no floor) —
   * matching the {@code aggregateMaterials()} bucket scheme so a material required in both
   * qualities is claimed separately per quality.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "quality_requirement", nullable = false, length = 8)
  private QualityRequirement qualityRequirement;

  /**
   * The squadron making the claim. Always an {@code OrgUnit} of kind {@code SQUADRON} (validated at
   * the service boundary); modelled as the {@link OrgUnit} base type so the FK targets the shared
   * {@code org_unit} table.
   */
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "claiming_org_unit_id", nullable = false)
  private OrgUnit claimingOrgUnit;

  /** The claimed partial quantity, in the material's own unit (SCU fractional vs PIECE whole). */
  @Column(name = "amount", nullable = false)
  private Double amount;

  /**
   * The user who last created/updated the claim, kept for the audit trail. Nullable to tolerate a
   * future system-initiated claim; in practice always set because the create path requires an
   * authenticated LOGISTICIAN+. The claim instant itself is {@code createdAt} from {@link
   * AbstractEntity}.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "claimed_by_user_id")
  private User claimedByUser;
}
