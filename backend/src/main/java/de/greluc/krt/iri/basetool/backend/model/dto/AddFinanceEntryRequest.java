package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.FinanceType;

import java.math.BigDecimal;

public record AddFinanceEntryRequest(
        String description,
        BigDecimal amount,
        FinanceType type
) {
}
