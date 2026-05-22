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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Operation JPA entity. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"missions"})
public class Operation extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OperationStatus status;

  @OneToMany(mappedBy = "operation")
  @OrderBy("plannedStartTime DESC")
  private Set<Mission> missions = new HashSet<>();

  /**
   * Squadron that owns this operation. Legacy field — kept authoritative during the R4 dual-write
   * soak. The plan-aligned {@link #owningOrgUnit} mirror field is kept in sync by
   * {@link #syncOwnerFields()} on every lifecycle event. A later release will drop this field
   * along with the matching DB column once {@code owning_org_unit_id} has soaked one full release
   * cycle in production.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owning_squadron_id", nullable = false)
  private Squadron owningSquadron;

  /**
   * Org-unit owner of this operation — the R4 dual-write mirror of {@link #owningSquadron}.
   * Pointed at the {@code owning_org_unit_id} FK column that Flyway migration V96 added in R1,
   * kept synchronised with the legacy field by {@link #syncOwnerFields()}. JPA-nullable for the
   * R4 soak window so a missed sync does not break inserts.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owning_org_unit_id")
  private OrgUnit owningOrgUnit;

  /**
   * Lifecycle hook that keeps {@link #owningSquadron} and {@link #owningOrgUnit} aligned on every
   * INSERT / UPDATE / SELECT path. See the matching method on {@link Mission#syncOwnerFields()}
   * for the rule (legacy field wins; reverse copy only when the legacy field is {@code null} and
   * the org-unit reference happens to point at a Squadron).
   */
  @PrePersist
  @PreUpdate
  @PostLoad
  private void syncOwnerFields() {
    if (owningSquadron != null) {
      owningOrgUnit = owningSquadron;
    } else if (owningOrgUnit instanceof Squadron s) {
      owningSquadron = s;
    }
  }
}
