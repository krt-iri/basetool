package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.math.BigDecimal;

/** Data transfer record carrying Mission Finance Entry Update payload. */
public record MissionFinanceEntryUpdateDto(
    String note, FinanceType type, BigDecimal amount, Long version) {}
