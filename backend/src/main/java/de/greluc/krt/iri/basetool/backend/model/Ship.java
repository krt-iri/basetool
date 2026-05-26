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
   * Org-unit owner of this ship. After R9 Step 2 dropped the legacy {@code owningSquadron} mirror
   * field together with the {@code syncOwnerFields()} lifecycle hook, callers stamp this field
   * directly via {@code OwnerScopeService.resolveOrgUnitForPickerOutput}; V100 drops the matching
   * {@code owning_squadron_id} column. {@code nullable = false} reflects V99's NOT NULL tightening
   * on the new column.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owning_org_unit_id", nullable = false)
  private OrgUnit owningOrgUnit;
}
