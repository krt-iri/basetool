package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemDto;
import de.greluc.krt.iri.basetool.backend.model.dto.LocationDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between Inventory Item entities and DTOs. */
@Mapper(
    componentModel = "spring",
    uses = {UserMapper.class, MaterialMapper.class})
public interface InventoryItemMapper {

  /**
   * Maps an {@link InventoryItem} entity to its outbound DTO. The nested {@code jobOrder} and
   * {@code mission} aggregates are flattened to id / display-name pairs so the client does not have
   * to traverse the join.
   */
  @Mapping(source = "jobOrder.id", target = "jobOrderId")
  @Mapping(source = "jobOrder.displayId", target = "jobOrderDisplayId")
  @Mapping(source = "mission.id", target = "missionId")
  @Mapping(source = "mission.name", target = "missionName")
  InventoryItemDto toDto(InventoryItem inventoryItem);

  /** Nested mapping for the item's {@link Location} (used as {@code uses} target). */
  LocationDto locationToDto(Location location);
}
