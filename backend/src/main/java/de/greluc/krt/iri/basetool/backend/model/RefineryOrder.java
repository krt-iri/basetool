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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Refinery Order JPA entity. */
@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RefineryOrder extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  @ManyToOne
  @JoinColumn(name = "location_id", nullable = false)
  private Location location;

  @ManyToOne
  @JoinColumn(name = "mission_id")
  private Mission mission;

  private Instant startedAt;

  @PositiveOrZero private Long durationMinutes;

  @ManyToOne
  @JoinColumn(name = "refining_method_id")
  private RefiningMethod refiningMethod;

  /**
   * Order costs. Must be >= 0. Optional: 0 is treated as "not set" on save and persisted as {@code
   * null}. The profit calculation treats {@code null} as 0.
   */
  @PositiveOrZero private Double expenses;

  /**
   * Other costs in addition to the regular {@link #expenses}. Must be >= 0. Optional: 0 is treated
   * as "not set" on save and persisted as {@code null}.
   */
  @PositiveOrZero private Double otherExpenses;

  /**
   * Revenue from selling raw ores ("Ore Sales"). Must be >= 0. Optional: 0 is treated as "not set"
   * on save and persisted as {@code null}.
   */
  @PositiveOrZero private Double oreSales;

  /**
   * Computed profit/loss: oreSales - expenses - otherExpenses. May be negative. Not persisted;
   * derived server-side from the raw values.
   */
  @Transient
  public Double getProfit() {
    double sales = oreSales != null ? oreSales : 0d;
    double costs = expenses != null ? expenses : 0d;
    double other = otherExpenses != null ? otherExpenses : 0d;
    return sales - costs - other;
  }

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RefineryOrderStatus status = RefineryOrderStatus.OPEN;

  @OneToMany(mappedBy = "refineryOrder", cascade = CascadeType.ALL, orphanRemoval = true)
  @ToString.Exclude
  @Valid
  private Set<RefineryGood> goods = new HashSet<>();

  /**
   * Squadron that owns this refinery order. Legacy field — kept authoritative during the R4
   * dual-write soak. The plan-aligned {@link #owningOrgUnit} mirror field is kept in sync by {@link
   * #syncOwnerFields()} on every lifecycle event. A later release will drop this field along with
   * the matching DB column.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owning_squadron_id", nullable = false)
  @ToString.Exclude
  private Squadron owningSquadron;

  /**
   * Org-unit owner of this refinery order — the R4 dual-write mirror of {@link #owningSquadron}.
   * Pointed at the {@code owning_org_unit_id} FK column that Flyway migration V96 added in R1, kept
   * synchronised with the legacy field by {@link #syncOwnerFields()}. JPA-nullable for the R4 soak
   * window so a missed sync does not break inserts.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owning_org_unit_id")
  @ToString.Exclude
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
