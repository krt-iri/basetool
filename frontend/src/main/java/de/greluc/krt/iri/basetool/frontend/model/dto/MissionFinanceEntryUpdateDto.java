package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.math.BigDecimal;

public record MissionFinanceEntryUpdateDto(
    String note, FinanceType type, BigDecimal amount, Long version) {}
