package de.greluc.krt.iri.basetool.frontend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/** Data transfer record carrying Material Price Overview payload. */
public record MaterialPriceOverviewDto(
    UUID id,
    String name,
    MaterialCategoryDto category,
    @JsonProperty("isIllegal") boolean isIllegal,
    @JsonProperty("isVolatileQt") boolean isVolatileQt,
    @JsonProperty("isVolatileTime") boolean isVolatileTime,
    BigDecimal minPriceBuy,
    BigDecimal maxPriceSell) {}
