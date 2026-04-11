package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.NumberFormat;
import java.util.UUID;

@Data
public class RefineryGoodForm {
    private UUID inputMaterialId;
    @Min(1)
    private Integer inputQuantity;
    private UUID outputMaterialId;
    @Min(1)
    private Integer outputQuantity;
    @Min(0)
    @Max(1000)
    private Integer quality;
}