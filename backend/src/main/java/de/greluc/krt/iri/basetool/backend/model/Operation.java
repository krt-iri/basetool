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
   * Squadron that owns this operation. Set at creation time from the caller's active squadron and
   * immutable afterwards. Gates read/write access at the service layer; admins can see all
   * squadrons. Kept JPA-nullable for Phase 1 until Flyway V86 tightens the column to NOT NULL.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owning_squadron_id", nullable = false)
  private Squadron owningSquadron;
}
