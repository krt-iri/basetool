package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class InventoryForm {
    @NotNull
    private UUID materialId;

    @NotNull
    private UUID locationId;

    @NotNull
    @Min(0)
    @Max(1000)
    private Integer quality;

    @NotNull
    @Min(0)
    private Double amount;

    private UUID jobOrderId;
    
    private UUID missionId;
    
    private UUID userId;

    private Boolean isGlobal;
    private Boolean personal = false;

    private String source;

    private Long version;
}
