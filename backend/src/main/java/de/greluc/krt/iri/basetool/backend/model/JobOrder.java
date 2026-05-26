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
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
   * Squadron that authored this order in the system. Legacy field — kept authoritative during the
   * R4 dual-write soak. The plan-aligned {@link #creatingOrgUnit} mirror field is kept in sync by
   * {@link #syncOwnerFields()} on every lifecycle event. Informational only — Job Orders are a
   * cross-squadron workspace and access is governed by the role/permission matrix, not by this
   * field (see MULTI_SQUADRON_PLAN.md, section 1).
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "creating_squadron_id", nullable = false)
  private Squadron creatingSquadron;

  /**
   * Org-unit author of this order — the R4 dual-write mirror of {@link #creatingSquadron}. Pointed
   * at the {@code creating_org_unit_id} FK column that Flyway migration V96 added in R1, kept
   * synchronised with the legacy field by {@link #syncOwnerFields()}. JPA-nullable for the R4 soak
   * window.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "creating_org_unit_id")
  private OrgUnit creatingOrgUnit;

  /**
   * Squadron the order is being executed for. Legacy field — may differ from {@link
   * #creatingSquadron} when one squadron creates orders on behalf of another. Editable by any
   * Logistician+ regardless of their own squadron. Kept authoritative during the R4 dual-write
   * soak; the plan-aligned {@link #requestingOrgUnit} mirror field is kept in sync by {@link
   * #syncOwnerFields()}. Informational only, not access-controlling.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requesting_squadron_id", nullable = false)
  private Squadron requestingSquadron;

  /**
   * Org-unit recipient of this order — the R4 dual-write mirror of {@link #requestingSquadron}.
   * Pointed at the {@code requesting_org_unit_id} FK column that Flyway migration V96 added in R1,
   * kept synchronised with the legacy field by {@link #syncOwnerFields()}. JPA-nullable for the R4
   * soak window.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requesting_org_unit_id")
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

  /** Adds a material and keeps the bidirectional back-reference in sync. */
  public void addMaterial(JobOrderMaterial material) {
    materials.add(material);
    material.setJobOrder(this);
  }

  /**
   * Lifecycle hook that keeps the two legacy squadron fields aligned with their R4 mirrors on every
   * INSERT / UPDATE / SELECT path. The legacy fields {@link #creatingSquadron} and {@link
   * #requestingSquadron} are authoritative during the R4 soak — they win when both halves of a pair
   * are set on the in-memory entity. The reverse copy runs only when the legacy field is {@code
   * null} and the org-unit reference happens to point at a Squadron, which covers the future case
   * where an R5 caller writes only the new field on a Squadron-owned order.
   */
  @PrePersist
  @PreUpdate
  @PostLoad
  private void syncOwnerFields() {
    if (creatingSquadron != null) {
      creatingOrgUnit = creatingSquadron;
    } else if (creatingOrgUnit instanceof Squadron s) {
      creatingSquadron = s;
    }
    if (requestingSquadron != null) {
      requestingOrgUnit = requestingSquadron;
    } else if (requestingOrgUnit instanceof Squadron s) {
      requestingSquadron = s;
    }
  }
}
