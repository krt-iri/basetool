package de.greluc.krt.iri.basetool.frontend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Data transfer record carrying Material Matrix Item payload.
 *
 * <p>{@code planetName} carries the <i>effective</i> planet name used to group terminals into UEX
 * planet systems (direct {@code terminal.planet_name}, indirect via the parent moon, or via a
 * like-named orbit in the same star system). {@code null} for terminals not attached to any planet.
 */
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
    String planetName,
    Boolean isJumpPoint,
    Boolean hasLoadingDock,
    Boolean isAutoLoad) {}
