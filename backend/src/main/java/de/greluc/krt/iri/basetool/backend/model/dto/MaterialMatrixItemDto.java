package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record MaterialMatrixItemDto(
    UUID materialId,
    String materialName,
    @JsonProperty("isIllegal") Boolean isIllegal,
    @JsonProperty("isVolatileQt") Boolean isVolatileQt,
    @JsonProperty("isVolatileTime") Boolean isVolatileTime,
    MaterialCategoryDto category,
    UUID terminalId,
    String terminalName,
    String terminalNickname,
    String starSystemName,
    BigDecimal priceBuy,
    BigDecimal priceSell,
    String cityName,
    String spaceStationName,
    String outpostName,
    Boolean isJumpPoint,
    Boolean hasLoadingDock,
    Boolean isAutoLoad) {
  public MaterialMatrixItemDto(
      UUID materialId,
      String materialName,
      Boolean isIllegal,
      Boolean isVolatileQt,
      Boolean isVolatileTime,
      UUID categoryId,
      String categoryName,
      Long categoryVersion,
      UUID terminalId,
      String terminalName,
      String terminalNickname,
      String starSystemName,
      BigDecimal priceBuy,
      BigDecimal priceSell,
      String cityName,
      String spaceStationName,
      String outpostName,
      Boolean isJumpPoint,
      Boolean hasLoadingDock,
      Boolean isAutoLoad) {
    this(
        materialId,
        materialName,
        isIllegal,
        isVolatileQt,
        isVolatileTime,
        categoryId != null
            ? new MaterialCategoryDto(categoryId, categoryName, categoryVersion)
            : null,
        terminalId,
        terminalName,
        terminalNickname,
        starSystemName,
        priceBuy,
        priceSell,
        cityName,
        spaceStationName,
        outpostName,
        isJumpPoint,
        hasLoadingDock,
        isAutoLoad);
  }
}
