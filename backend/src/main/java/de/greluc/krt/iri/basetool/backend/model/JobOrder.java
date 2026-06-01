package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Job Order JPA entity. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "job_order")
public class JobOrder extends AbstractEntity<UUID> {

  /**
   * Maximum length, in characters, of the optional {@link #comment}. The same bound is enforced at
   * the DTO validation boundary ({@code @Size}), in the {@code comment} column definition, and by
   * the Flyway migration, so the cap holds end-to-end for untrusted (incl. anonymous) input.
   */
  public static final int COMMENT_MAX_LENGTH = 1000;

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "display_id", insertable = false, updatable = false)
  @org.hibernate.annotations.Generated
  private Integer displayId;

  /**
   * Org unit responsible for <em>processing</em> this order — a profit-eligible squadron or
   * Spezialkommando (eligibility enforced at the service layer via {@code
   * OrgUnit#isProfitEligible}). Stamped at create time and editable afterwards only through the
   * dedicated reassignment endpoint ({@code PATCH /api/v1/orders/{id}/responsible-org-unit}). This
   * field governs visibility (Phase 3, #343): a squadron-responsible order is private to that
   * squadron + admins, an SK-responsible order is public to all squadrons. The requester does NOT
   * grant visibility. {@code nullable = false} reflects V130's NOT NULL tightening after the Phase
   * 3 backfill copied each legacy order's retired {@code creating_org_unit_id} onto this column.
   * The retired {@code creating_org_unit_id} column lives on (nullable, unmapped) until the
   * destructive cleanup release per V129.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "responsible_org_unit_id", nullable = false)
  private OrgUnit responsibleOrgUnit;

  /**
   * Org unit that placed the order and ultimately receives the material/item — the customer. Any
   * squadron or Spezialkommando (no profit-eligibility restriction; other Kartell departments may
   * place orders). Mandatory at create time, editable by any Logistician+ through the regular
   * update path. Informational only; it does NOT grant visibility (a private order is visible to
   * its responsible org unit + admins only, not to the requester — see Phase 3 / #343). {@code
   * nullable = false} reflects V99's NOT NULL tightening, still in force.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requesting_org_unit_id", nullable = false)
  private OrgUnit requestingOrgUnit;

  @Column private String handle;

  @Column private Integer priority;

  /**
   * Optional free-text comment captured from the order creator at creation time (context, delivery
   * notes, …). Stored as plain text and always rendered HTML-escaped in the UI — never interpreted
   * as markup. Because anonymous users may create job orders this value is fully untrusted: its
   * length is bounded to {@value #COMMENT_MAX_LENGTH} characters at the DTO boundary and by the
   * column definition, and it is never written to application logs. {@code null} when the creator
   * left the field empty.
   */
  @Column(name = "comment", length = COMMENT_MAX_LENGTH)
  private String comment;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private JobOrderStatus status = JobOrderStatus.OPEN;

  /**
   * Order kind. {@link JobOrderType#MATERIAL} (the default, and the value backfilled onto every
   * pre-existing row by migration V123) requests raw materials directly via {@link #materials};
   * {@link JobOrderType#ITEM} requests finished items via {@link #items}, from which the material
   * requirements are derived. Never {@code null}.
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private JobOrderType type = JobOrderType.MATERIAL;

  @OneToMany(
      mappedBy = "jobOrder",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @Builder.Default
  private Set<JobOrderMaterial> materials = new HashSet<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "job_order_assignees",
      joinColumns = @JoinColumn(name = "job_order_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @Builder.Default
  private Set<User> assignees = new HashSet<>();

  @OneToMany(
      mappedBy = "jobOrder",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @Builder.Default
  private Set<JobOrderHandover> handovers = new HashSet<>();

  /**
   * Ordered finished-item lines, populated only for {@link JobOrderType#ITEM} orders. Empty for
   * material orders.
   */
  @OneToMany(
      mappedBy = "jobOrder",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @Builder.Default
  private Set<JobOrderItem> items = new HashSet<>();

  /**
   * Item-handover fulfilment events, populated only for {@link JobOrderType#ITEM} orders. Empty for
   * material orders (which use {@link #handovers}).
   */
  @OneToMany(
      mappedBy = "jobOrder",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @Builder.Default
  private Set<JobOrderItemHandover> itemHandovers = new HashSet<>();

  /**
   * Adds a material and keeps the bidirectional back-reference in sync.
   *
   * @param material the child material row to attach; mutated so its {@code jobOrder} back-link
   *     points at this order.
   */
  public void addMaterial(JobOrderMaterial material) {
    materials.add(material);
    material.setJobOrder(this);
  }

  /**
   * Adds an ordered finished-item line and keeps the bidirectional back-reference in sync.
   *
   * @param item the child item line to attach; mutated so its {@code jobOrder} back-link points at
   *     this order.
   */
  public void addItem(JobOrderItem item) {
    items.add(item);
    item.setJobOrder(this);
  }

  /**
   * Adds an item-handover fulfilment event and keeps the bidirectional back-reference in sync.
   *
   * @param itemHandover the child handover to attach; mutated so its {@code jobOrder} back-link
   *     points at this order.
   */
  public void addItemHandover(JobOrderItemHandover itemHandover) {
    itemHandovers.add(itemHandover);
    itemHandover.setJobOrder(this);
  }
}
