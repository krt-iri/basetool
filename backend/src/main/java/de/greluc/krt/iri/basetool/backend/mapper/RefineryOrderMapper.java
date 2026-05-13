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

@Mapper(
    componentModel = "spring",
    uses = {UserMapper.class, MaterialMapper.class},
    unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface RefineryOrderMapper {
  @Mapping(target = "profit", expression = "java(computeProfit(entity))")
  RefineryOrderDto toDto(RefineryOrder entity);

  @Mapping(target = "profit", expression = "java(computeProfit(entity))")
  RefineryOrderListDto toListDto(RefineryOrder entity);

  @Mapping(target = "owner", ignore = true)
  RefineryOrder toEntity(RefineryOrderDto dto);

  /**
   * Computes profit/loss = oreSales - expenses - otherExpenses for the order. Null values are
   * treated as 0 so that legacy data does not trigger an NPE.
   */
  default Double computeProfit(RefineryOrder entity) {
    if (entity == null) return 0d;
    double sales = entity.getOreSales() != null ? entity.getOreSales() : 0d;
    double costs = entity.getExpenses() != null ? entity.getExpenses() : 0d;
    double other = entity.getOtherExpenses() != null ? entity.getOtherExpenses() : 0d;
    return sales - costs - other;
  }

  LocationDto locationToDto(Location location);

  default Mission missionDtoToMission(MissionDto dto) {
    if (dto == null) return null;
    Mission mission = new Mission();
    mission.setId(dto.id());
    return mission;
  }
}
