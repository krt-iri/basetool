package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Inventory Item JPA entity. */
@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  // @ToString.Exclude on every LAZY association so a call to toString() outside
  // of a Hibernate session (e.g. from a log statement after the transaction
  // has committed) does not trigger LazyInitializationException. Matches the
  // pattern already used in Mission / Operation / RefineryOrder.
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  @ToString.Exclude
  private User user;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "material_id", nullable = false)
  @ToString.Exclude
  private Material material;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "location_id", nullable = false)
  @ToString.Exclude
  private Location location;

  @Min(0)
  @Max(1000)
  @Column(nullable = false)
  private Integer quality;

  @Min(0)
  @Column(nullable = false)
  private Double amount; // SCU

  @Column(nullable = false)
  private Boolean personal = false;

  @ManyToOne(optional = true, fetch = FetchType.LAZY)
  @JoinColumn(name = "job_order_id", nullable = true)
  @ToString.Exclude
  private JobOrder jobOrder;

  @ManyToOne(optional = true, fetch = FetchType.LAZY)
  @JoinColumn(name = "mission_id", nullable = true)
  @ToString.Exclude
  private Mission mission;

  @Column(name = "note", length = 1000)
  private String note;

  @Column(nullable = false)
  private Boolean delivered = false;

  /**
   * Org-unit owner of this inventory item (the org unit whose physical stock this row represents),
   * or {@code null} for an <em>ownerless personal</em> item — one recorded by a user who belongs to
   * no Staffel/SK. Such an item is attributable solely through {@link #user} and is
   * visible/editable only by that user (plus admins in all-scopes mode); it never surfaces in an
   * org unit's Lager-View. Callers stamp this field via {@code
   * OwnerScopeService.resolveOrgUnitForPickerOutputNullable}. V132 dropped the {@code NOT NULL}
   * constraint V102 had added, which is why the column — and therefore this {@code @JoinColumn} —
   * is nullable.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owning_org_unit_id", nullable = true)
  @ToString.Exclude
  private OrgUnit owningOrgUnit;
}
