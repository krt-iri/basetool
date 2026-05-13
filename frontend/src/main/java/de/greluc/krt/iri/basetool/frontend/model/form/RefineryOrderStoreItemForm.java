package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;

/**
 * Form-backing object for a single material row in the store dialog of a refinery order.
 *
 * <p>Amount validation follows the project-wide convention for amount input fields: decimal number,
 * &gt;= 0, max. 3 decimal places. The note is optional and is attached to the created {@code
 * InventoryItem} on storage.
 */
@Data
public class RefineryOrderStoreItemForm {

  @NotNull private UUID materialId;

  private String materialName;

  private String quantityType;

  @NotNull private UUID locationId;

  @NotNull
  @Min(0)
  @Max(1000)
  private Integer quality;

  @NotNull
  @DecimalMin(value = "0.0", inclusive = true)
  @Digits(integer = 15, fraction = 3)
  private Double amount;

  private Boolean amountFixed;

  private UUID userId;

  private UUID jobOrderId;

  @Size(max = 1000)
  private String note;
}
