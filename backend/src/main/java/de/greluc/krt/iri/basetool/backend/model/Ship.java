package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Ship JPA entity. */
@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Ship extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String name;

  @ManyToOne
  @JoinColumn(name = "ship_type_id", nullable = false)
  private ShipType shipType;

  @NotBlank(message = "{validation.insurance.required}")
  @Pattern(
      regexp = "^(0|([1-9]|[1-9][0-9]|1[0-1][0-9]|120)|LTI)$",
      message = "{validation.insurance.pattern}")
  private String insurance;

  @ManyToOne
  @JoinColumn(name = "location_id")
  private Location location;

  private boolean fitted;

  @ManyToOne
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  /**
   * Squadron that owns this ship. Legacy field — kept authoritative during the R4 dual-write soak.
   * The plan-aligned {@link #owningOrgUnit} mirror field is kept in sync by {@link
   * #syncOwnerFields()} on every lifecycle event. A later release will drop this field along with
   * the matching DB column.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owning_squadron_id")
  private Squadron owningSquadron;

  /**
   * Org-unit owner of this ship — the R4 dual-write mirror of {@link #owningSquadron}. After R9
   * Step 1 callers write this field directly; the legacy {@link #owningSquadron} is filled by
   * the lifecycle hook only when the resolved OrgUnit is a Squadron. {@code nullable = false}
   * reflects V99's NOT NULL tightening on the DB column.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owning_org_unit_id", nullable = false)
  private OrgUnit owningOrgUnit;

  /**
   * Lifecycle hook that keeps {@link #owningSquadron} and {@link #owningOrgUnit} aligned on every
   * INSERT / UPDATE / SELECT path. See the matching method on {@link Mission#syncOwnerFields()} for
   * the rule.
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
