package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.dto.LocationDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderListDto;
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
