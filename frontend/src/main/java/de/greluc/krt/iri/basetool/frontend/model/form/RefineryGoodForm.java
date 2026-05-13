package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.Data;

/** Form-binding object for Refinery Good input. */
@Data
public class RefineryGoodForm {
  private UUID inputMaterialId;

  @Min(1) private Integer inputQuantity;

  private UUID outputMaterialId;

  @Min(1) private Integer outputQuantity;

  @Min(0) @Max(1000) private Integer quality;
}
