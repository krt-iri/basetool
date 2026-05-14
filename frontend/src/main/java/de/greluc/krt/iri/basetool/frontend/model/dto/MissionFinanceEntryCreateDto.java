package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Data transfer record carrying Mission Finance Entry Create payload. */
public record MissionFinanceEntryCreateDto(
    UUID missionId, UUID participantId, String note, FinanceType type, BigDecimal amount) {}
