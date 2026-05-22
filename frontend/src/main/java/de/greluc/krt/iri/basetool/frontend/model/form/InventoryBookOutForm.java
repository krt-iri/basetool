package de.greluc.krt.iri.basetool.frontend.model.form;

import de.greluc.krt.iri.basetool.frontend.model.dto.CheckoutType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

/** Form-binding object for Inventory Book Out input. */
@Data
public class InventoryBookOutForm {

  @NotNull
  @Min(0)
  private Double amount;

  private Double targetAmount;

  private Double maxAmount;

  private UUID targetUserId;

  private UUID targetLocationId;

  private CheckoutType type;

  private String terminal;

  @Min(0)
  private BigDecimal sellAmount;

  private Boolean isGlobal;

  private Long version;

  /**
   * R5.d.g owner-picker output: the {@code OrgUnit} the destination's new inventory row should be
   * stamped on. Only honoured for {@link CheckoutType#TRANSFER}. {@code null} preserves the legacy
   * "stamp target user's home Staffel" behaviour on the backend (covers the single-membership case
   * 100 % of users hit today).
   */
  private UUID targetOwningOrgUnitId;
}
