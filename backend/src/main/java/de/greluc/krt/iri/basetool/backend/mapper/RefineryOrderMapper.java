package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderListDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.dto.LocationDto;

@Mapper(componentModel = "spring", uses = {UserMapper.class, MaterialMapper.class}, unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface RefineryOrderMapper {
    RefineryOrderDto toDto(RefineryOrder entity);

    RefineryOrderListDto toListDto(RefineryOrder entity);
    
    @Mapping(target = "owner", ignore = true)
    RefineryOrder toEntity(RefineryOrderDto dto);
    
    LocationDto locationToDto(Location location);

    default Mission missionDtoToMission(MissionDto dto) {
        if (dto == null) return null;
        Mission mission = new Mission();
        mission.setId(dto.id());
        return mission;
    }
}
