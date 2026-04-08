package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MissionFinanceEntryCreateDto(
        UUID missionId,
        UUID participantId,
        String note,
        FinanceType type,
        BigDecimal amount
) {
}