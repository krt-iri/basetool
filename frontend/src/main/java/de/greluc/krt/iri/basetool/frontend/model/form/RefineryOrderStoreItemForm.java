package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.NumberFormat;

import java.util.UUID;

@Data
public class RefineryOrderStoreItemForm {
    
    @NotNull
    private UUID materialId;
    
    private String materialName;
    
    private String quantityType;
    
    @NotNull
    private UUID locationId;
    
    @NotNull
    @Min(0)
    @Max(1000)
    private Integer quality;
    
    @NotNull
    @Min(0)
    private Double amount;
    
    private Boolean amountFixed;
    
    private UUID userId;
    
    private UUID jobOrderId;
}
