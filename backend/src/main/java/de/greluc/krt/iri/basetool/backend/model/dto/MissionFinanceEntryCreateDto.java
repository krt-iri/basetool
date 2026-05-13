package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.FinanceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** Data transfer record carrying Mission Finance Entry Create payload. */
public record MissionFinanceEntryCreateDto(
    @NotNull UUID missionId,
    @NotNull UUID participantId,
    String note,
    @NotNull FinanceType type,
    @NotNull @DecimalMin("0.0") BigDecimal amount) {}
