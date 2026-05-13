package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Data transfer record carrying Mission Finance Entry payload. */
public record MissionFinanceEntryDto(
    UUID id,
    UUID missionId,
    MissionParticipantDto participant,
    String note,
    FinanceType type,
    BigDecimal amount,
    Long version) {}
