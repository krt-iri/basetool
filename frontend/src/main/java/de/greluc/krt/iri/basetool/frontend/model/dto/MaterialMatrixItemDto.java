package de.greluc.krt.iri.basetool.frontend.model.dto;

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
    Boolean isAutoLoad) {}
