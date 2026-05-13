package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.FinanceType;
import java.math.BigDecimal;

/** Inbound request payload for the Add Finance Entry operation. */
public record AddFinanceEntryRequest(String description, BigDecimal amount, FinanceType type) {}
