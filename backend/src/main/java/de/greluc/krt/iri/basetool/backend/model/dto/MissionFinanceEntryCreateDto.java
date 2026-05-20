package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.FinanceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Data transfer record carrying Mission Finance Entry Create payload. The {@code @Size} /
 * {@code @DecimalMax} caps cap the anonymous attack surface — without them an unauthenticated
 * caller could push a 100 MB {@code note} or a {@code 1e100} amount through the public create-entry
 * endpoint (audit finding C-2).
 */
public record MissionFinanceEntryCreateDto(
    @NotNull UUID missionId,
    @NotNull UUID participantId,
    @Size(max = 2000) String note,
    @NotNull FinanceType type,
    @NotNull @DecimalMin("0.0") @DecimalMax("1000000000.0") BigDecimal amount) {}
