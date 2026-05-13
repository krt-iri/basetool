package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemDto;
import de.greluc.krt.iri.basetool.backend.model.dto.LocationDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
    componentModel = "spring",
    uses = {UserMapper.class, MaterialMapper.class})
public interface InventoryItemMapper {

  @Mapping(source = "jobOrder.id", target = "jobOrderId")
  @Mapping(source = "jobOrder.displayId", target = "jobOrderDisplayId")
  @Mapping(source = "mission.id", target = "missionId")
  @Mapping(source = "mission.name", target = "missionName")
  InventoryItemDto toDto(InventoryItem inventoryItem);

  LocationDto locationToDto(Location location);
}
