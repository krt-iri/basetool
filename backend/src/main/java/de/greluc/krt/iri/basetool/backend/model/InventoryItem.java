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
   * Squadron that owns this inventory item (i.e., the squadron whose physical stock this row
   * represents). Set at creation time from the caller's active squadron and immutable afterwards.
   * The direct Lager-View filters by this column; an item linked to a job order via {@link
   * #jobOrder} additionally surfaces in that order's UI for ALL squadrons (cross-squadron
   * workspace, see MULTI_SQUADRON_PLAN.md). Kept JPA-nullable for Phase 1 until Flyway V86 tightens
   * the column to NOT NULL.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owning_squadron_id")
  @ToString.Exclude
  private Squadron owningSquadron;
}
