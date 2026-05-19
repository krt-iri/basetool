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
   * Legacy free-text squadron column, semantically superseded by {@link #requestingSquadron} and
   * {@link #creatingSquadron}. Still written by the service layer in this release as a safety net
   * during the two-phase migration; Flyway V87 removes the JPA field and V88 drops the DB column.
   * New read paths must use {@link #requestingSquadron} instead - this field stays mirrored only to
   * keep the legacy {@code NOT NULL} column populated until V87 lifts the constraint.
   */
  @Column(nullable = false)
  private String squadron;

  /**
   * Squadron that authored this order in the system. Set automatically at creation time from the
   * caller's active squadron context and immutable afterwards. Informational only - Job Orders are
   * a cross-squadron workspace and access is governed by the role/permission matrix, not by this
   * field (see MULTI_SQUADRON_PLAN.md, section 1).
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "creating_squadron_id")
  private Squadron creatingSquadron;

  /**
   * Squadron the order is being executed for. May differ from {@link #creatingSquadron} when one
   * squadron creates orders on behalf of another. Editable by any Logistician+ regardless of their
   * own squadron - {@code @Version} on the aggregate handles concurrent edits. Informational only,
   * not access-controlling.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requesting_squadron_id")
  private Squadron requestingSquadron;

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
}
