package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.Data;

/** Form-binding object for Ship input. */
@Data
public class ShipForm {
  private String name;

  @NotNull(message = "{ship.validation.shiptype.required}")
  private UUID shipTypeId;

  @NotBlank(message = "{ship.validation.insurance.required}")
  @Pattern(
      regexp = "^(0|([1-9]|[1-9][0-9]|1[0-1][0-9]|120)|LTI)$",
      message = "{validation.insurance.pattern}")
  private String insurance;

  private UUID locationId;
  private boolean fitted;
  private Long version;

  /**
   * R5.d.f owner-picker output: the {@code OrgUnit} the new ship should be stamped on. {@code null}
   * when the caller has at most one OrgUnit membership (fragment is hidden and the backend falls
   * back to {@code user.getSquadron()} via the shared resolver).
   */
  private UUID owningOrgUnitId;
}
