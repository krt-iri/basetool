package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Data transfer record carrying Material Matrix Item payload.
 *
 * <p>The {@code planetName} field carries the <i>effective</i> planet name that groups a terminal
 * into its UEX planet system: it is resolved on the SQL side via {@code COALESCE} over {@code
 * terminal.planet_name}, {@code moon.planet_name} (indirectly via {@code terminal.moon_name}) and
 * finally the {@code Planet} whose own name matches {@code terminal.orbit_name} within the same
 * star system. It is {@code null} for terminals that are not attached to any planet (e.g. raw
 * jump-point or Lagrange-only stations).
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
    Boolean isAutoLoad) {
  /**
   * Convenience constructor used by JPA tuple-mapping queries that surface the {@code
   * MaterialCategory} fields as flat columns; wraps them into a {@link MaterialCategoryDto}.
   *
   * @param materialId material id
   * @param materialName material display name
   * @param isIllegal contraband flag
   * @param isVolatileQt quick-decay flag
   * @param isVolatileTime time-decay flag
   * @param categoryId category id (may be {@code null})
   * @param categoryName category name (may be {@code null})
   * @param categoryVersion category optimistic-lock version (may be {@code null})
   * @param terminalId terminal id
   * @param terminalName terminal display name
   * @param terminalNickname terminal short name
   * @param starSystemName parent star system
   * @param priceBuy current buy price at the terminal
   * @param priceSell current sell price at the terminal
   * @param cityName parent city, if any
   * @param spaceStationName parent space station, if any
   * @param outpostName parent outpost, if any
   * @param planetName effective planet name (direct, via moon, or via like-named orbit); {@code
   *     null} when the terminal is not attached to a planet
   * @param isJumpPoint whether the parent station is a jump point
   * @param hasLoadingDock whether the terminal has a loading dock
   * @param isAutoLoad whether the terminal supports automatic cargo loading
   */
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
      String planetName,
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
        planetName,
        isJumpPoint,
        hasLoadingDock,
        isAutoLoad);
  }
}
