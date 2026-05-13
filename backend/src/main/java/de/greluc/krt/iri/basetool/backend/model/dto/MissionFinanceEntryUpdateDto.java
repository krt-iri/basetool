package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.FinanceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record MissionFinanceEntryUpdateDto(
    String note,
    @NotNull FinanceType type,
    @NotNull @DecimalMin("0.0") BigDecimal amount,
    @NotNull Long version) {}
