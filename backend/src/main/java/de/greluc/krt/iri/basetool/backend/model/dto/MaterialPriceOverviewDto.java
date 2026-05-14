package de.greluc.krt.iri.basetool.backend.model.dto;

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
    BigDecimal maxPriceSell) {
  /**
   * Convenience constructor used by JPA tuple-mapping queries: flattened category columns get
   * wrapped into a {@link MaterialCategoryDto}, and UEX-style {@code Integer} flag columns are
   * normalised to {@code boolean}.
   */
  public MaterialPriceOverviewDto(
      UUID id,
      String name,
      UUID categoryId,
      String categoryName,
      Long categoryVersion,
      Integer isIllegal,
      Integer isVolatileQt,
      Integer isVolatileTime,
      BigDecimal minPriceBuy,
      BigDecimal maxPriceSell) {
    this(
        id,
        name,
        categoryId != null
            ? new MaterialCategoryDto(categoryId, categoryName, categoryVersion)
            : null,
        isIllegal != null && isIllegal == 1,
        isVolatileQt != null && isVolatileQt == 1,
        isVolatileTime != null && isVolatileTime == 1,
        minPriceBuy,
        maxPriceSell);
  }

  /**
   * Test-only convenience constructor that accepts flattened category columns plus the boolean
   * flags directly (no Integer normalisation needed).
   */
  public MaterialPriceOverviewDto(
      UUID id,
      String name,
      UUID categoryId,
      String categoryName,
      Long categoryVersion,
      boolean isIllegal,
      boolean isVolatileQt,
      boolean isVolatileTime,
      BigDecimal minPriceBuy,
      BigDecimal maxPriceSell) {
    this(
        id,
        name,
        categoryId != null
            ? new MaterialCategoryDto(categoryId, categoryName, categoryVersion)
            : null,
        isIllegal,
        isVolatileQt,
        isVolatileTime,
        minPriceBuy,
        maxPriceSell);
  }
}
