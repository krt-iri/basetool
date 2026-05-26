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

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "display_id", insertable = false, updatable = false)
  @org.hibernate.annotations.Generated
  private Integer displayId;

  /**
   * Org-unit author of this order. After R9 Step 2 dropped the legacy {@code creatingSquadron}
   * mirror field together with the {@code syncOwnerFields()} lifecycle hook, callers stamp this
   * field directly via {@code OwnerScopeService.resolveOrgUnitForPickerOutput}; V100 drops the
   * matching {@code creating_squadron_id} column. {@code nullable = false} reflects V99's NOT NULL
   * tightening. Informational only — Job Orders are a cross-squadron workspace and access is
   * governed by the role/permission matrix, not by this field (see MULTI_SQUADRON_PLAN.md, section
   * 1).
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "creating_org_unit_id", nullable = false)
  private OrgUnit creatingOrgUnit;

  /**
   * Org-unit recipient of this order — may differ from {@link #creatingOrgUnit} when one org unit
   * creates orders on behalf of another. After R9 Step 2 dropped the legacy {@code
   * requestingSquadron} mirror field together with the {@code syncOwnerFields()} lifecycle hook,
   * callers stamp this field directly; V100 drops the matching {@code requesting_squadron_id}
   * column. Editable by any Logistician+ regardless of their own org unit. Informational only, not
   * access-controlling. {@code nullable = false} reflects V99's NOT NULL tightening.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requesting_org_unit_id", nullable = false)
  private OrgUnit requestingOrgUnit;

  @Column private String handle;

  @Column private Integer priority;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private JobOrderStatus status = JobOrderStatus.OPEN;

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
   * Adds a material and keeps the bidirectional back-reference in sync.
   *
   * @param material the child material row to attach; mutated so its {@code jobOrder} back-link
   *     points at this order.
   */
  public void addMaterial(JobOrderMaterial material) {
    materials.add(material);
    material.setJobOrder(this);
  }
}
