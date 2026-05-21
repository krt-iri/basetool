package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.dto.LocationDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryGoodDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderListDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between Refinery Order entities and DTOs. */
@Mapper(
    componentModel = "spring",
    uses = {UserMapper.class, MaterialMapper.class, SquadronMapper.class},
    unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface RefineryOrderMapper {
  /**
   * Maps a {@link RefineryOrder} entity to its full DTO; the {@code profit} field is derived from
   * {@link #computeProfit}.
   */
  @Mapping(target = "profit", expression = "java(computeProfit(entity))")
  RefineryOrderDto toDto(RefineryOrder entity);

  /**
   * Same as {@link #toDto(RefineryOrder)} but additionally fills {@code yieldBonusPercent} on every
   * good whose {@code inputMaterial} appears in {@code yieldByMaterialId}. Goods whose material is
   * not in the map stay {@code null} (caller must distinguish "no data" from "explicit zero" — a 0%
   * yield row from UEX is a perfectly valid value).
   *
   * @param entity the refinery order to map, may be {@code null}
   * @param yieldByMaterialId per-material yield bonus map (see {@code
   *     RefineryOrderService.getYieldBonusByMaterialForLocation})
   * @return enriched DTO, or {@code null} when {@code entity} is {@code null}
   */
  default RefineryOrderDto toDto(RefineryOrder entity, Map<UUID, Integer> yieldByMaterialId) {
    RefineryOrderDto base = toDto(entity);
    if (base == null
        || base.goods() == null
        || yieldByMaterialId == null
        || yieldByMaterialId.isEmpty()) {
      return base;
    }
    List<RefineryGoodDto> enriched =
        base.goods().stream().map(g -> applyYield(g, yieldByMaterialId)).toList();
    return new RefineryOrderDto(
        base.id(),
        base.owner(),
        base.location(),
        base.mission(),
        base.startedAt(),
        base.durationMinutes(),
        base.expenses(),
        base.otherExpenses(),
        base.oreSales(),
        base.profit(),
        base.refiningMethod(),
        base.status(),
        enriched,
        base.owningSquadron(),
        base.version());
  }

  /** Slim list-row DTO of a {@link RefineryOrder}; reuses the same profit computation. */
  @Mapping(target = "profit", expression = "java(computeProfit(entity))")
  RefineryOrderListDto toListDto(RefineryOrder entity);

  /**
   * Builds a new {@link RefineryOrder} entity from the inbound DTO. {@code owner} is owned by the
   * service (resolved from the JWT) and stripped here.
   */
  @Mapping(target = "owner", ignore = true)
  RefineryOrder toEntity(RefineryOrderDto dto);

  /**
   * Computes profit/loss = oreSales - expenses - otherExpenses for the order. Null values are
   * treated as 0 so that legacy data does not trigger an NPE.
   */
  default Double computeProfit(RefineryOrder entity) {
    if (entity == null) {
      return 0d;
    }
    double sales = entity.getOreSales() != null ? entity.getOreSales() : 0d;
    double costs = entity.getExpenses() != null ? entity.getExpenses() : 0d;
    double other = entity.getOtherExpenses() != null ? entity.getOtherExpenses() : 0d;
    return sales - costs - other;
  }

  /**
   * Returns a copy of {@code good} with {@code yieldBonusPercent} set from {@code
   * yieldByMaterialId} when a row for the input material exists; the original DTO when the lookup
   * misses or {@code inputMaterial} is null. Records are immutable, hence the copy.
   */
  private static RefineryGoodDto applyYield(
      RefineryGoodDto good, Map<UUID, Integer> yieldByMaterialId) {
    if (good == null || good.inputMaterial() == null || good.inputMaterial().id() == null) {
      return good;
    }
    Integer bonus = yieldByMaterialId.get(good.inputMaterial().id());
    if (bonus == null) {
      return good;
    }
    return new RefineryGoodDto(
        good.id(),
        good.inputMaterial(),
        good.inputQuantity(),
        good.outputMaterial(),
        good.outputQuantity(),
        good.quality(),
        bonus);
  }

  /** Nested mapping for the order's {@link Location}. */
  LocationDto locationToDto(Location location);

  /**
   * MapStruct default that resolves an incoming {@link MissionDto} to a JPA stub Mission carrying
   * only the id - the persistence provider then materialises the managed instance on persist.
   */
  default Mission missionDtoToMission(MissionDto dto) {
    if (dto == null) {
      return null;
    }
    Mission mission = new Mission();
    mission.setId(dto.id());
    return mission;
  }
}
