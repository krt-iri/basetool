package de.greluc.krt.iri.basetool.backend.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProfitCalculationDto(
    UUID materialId,
    String materialName,
    BigDecimal minBuyPrice,
    BigDecimal maxSellPrice,
    BigDecimal profitPerScu,
    BigDecimal marginPercent,
    BigDecimal fullLoadCost,
    BigDecimal maxProfitFullLoad) {}
