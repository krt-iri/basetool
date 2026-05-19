package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
   * Squadron that owns this ship. Set at creation time from the caller's active squadron and
   * immutable afterwards. The hangar view filters by this column; admins see ships of all
   * squadrons. Kept JPA-nullable for Phase 1 until Flyway V86 tightens the column to NOT NULL.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owning_squadron_id")
  private Squadron owningSquadron;
}
