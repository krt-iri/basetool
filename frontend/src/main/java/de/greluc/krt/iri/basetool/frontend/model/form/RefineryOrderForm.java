package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class RefineryOrderForm {
    private String startedAt;
    @Min(0)
    private Integer durationHours;
    @Min(0)
    private Integer durationMinutes;
    @Positive
    private Double expenses;
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