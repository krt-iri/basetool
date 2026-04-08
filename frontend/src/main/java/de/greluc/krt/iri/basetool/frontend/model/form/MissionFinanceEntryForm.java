package de.greluc.krt.iri.basetool.frontend.model.form;

import de.greluc.krt.iri.basetool.frontend.model.dto.FinanceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class MissionFinanceEntryForm {

    private UUID id;

    private UUID missionId;

    private UUID participantId;

    private String note;

    @NotNull(message = "{finance.validation.type.null}")
    private FinanceType type;

    @NotNull(message = "{finance.validation.amount.null}")
    @DecimalMin(value = "0.0", message = "{finance.validation.amount.min}")
    private BigDecimal amount;

    private Long version;
}