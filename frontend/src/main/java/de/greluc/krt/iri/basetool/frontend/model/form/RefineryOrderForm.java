package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class RefineryOrderForm {
    private String startedAt;
    @NotNull
    @Min(0)
    private Integer durationHours = 0;
    @NotNull
    @Min(0)
    @Max(59)
    private Integer durationMinutes = 0;
    @NotNull
    @Min(0)
    private Double expenses = 0d;
    /** Einnahmen durch den Verkauf roher Erze (Ore Sales). Ganzzahl >= 0, Default 0. */
    @NotNull
    @Min(0)
    private Double oreSales = 0d;
    private UUID ownerId;
    private UUID refiningMethodId;
    private UUID locationId;
    private UUID missionId;
    private de.greluc.krt.iri.basetool.frontend.model.dto.RefineryOrderStatus status;
    private Long version;
    private String source;
    @Valid
    private List<RefineryGoodForm> goods = new ArrayList<>();
    
    public RefineryOrderForm() {
        goods.add(new RefineryGoodForm());
    }
}