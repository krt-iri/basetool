package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.FinanceType;
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
