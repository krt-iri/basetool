package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.*;

/** Refinery Order JPA entity. */
@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RefineryOrder extends AbstractEntity<UUID> {

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
  @Valid private Set<RefineryGood> goods = new HashSet<>();
}
