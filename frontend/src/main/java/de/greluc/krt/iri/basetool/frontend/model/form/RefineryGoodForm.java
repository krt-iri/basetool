package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

/**
 * Form-binding object for Refinery Good input.
 *
 * <p>{@code outputQuantity} is required and must be at least 1 — the user explicitly enters the
 * expected yield per material and the value drives the SCU display + later refinery payout. {@code
 * quality} defaults to 0; if the user leaves the field empty the controller normalises the bound
 * {@code null} back to 0 before sending to the backend (see {@code RefineryOrderPageController}).
 */
@Data
public class RefineryGoodForm {
  private UUID inputMaterialId;

  @NotNull
  @Min(1)
  private Integer inputQuantity;

  private UUID outputMaterialId;

  @NotNull
  @Min(1)
  private Integer outputQuantity;

  @Min(0)
  @Max(1000)
  private Integer quality = 0;
}
