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
    uses = {UserMapper.class, MaterialMapper.class, SquadronMapper.class})
public interface InventoryItemMapper {

  /**
   * Maps an {@link InventoryItem} entity to its outbound DTO. The nested {@code jobOrder} and
   * {@code mission} aggregates are flattened to id / display-name pairs so the client does not have
   * to traverse the join.
   *
   * <p>After R9 Step 2 the inventory-item entity exposes {@code owningOrgUnit} (typed {@code
   * OrgUnit}); the DTO still publishes {@code owningSquadron} as {@code SquadronReferenceDto} for
   * API stability. The explicit mapping routes the source through {@code
   * SquadronMapper.orgUnitToReferenceDto} so SK-owned stock surfaces as {@code null} on the wire
   * while Staffel-owned stock continues to project as before.
   *
   * @param inventoryItem the inventory-item entity to project; {@code null} returns {@code null}.
   * @return the populated inventory-item DTO.
   */
  @Mapping(source = "jobOrder.id", target = "jobOrderId")
  @Mapping(source = "jobOrder.displayId", target = "jobOrderDisplayId")
  @Mapping(source = "mission.id", target = "missionId")
  @Mapping(source = "mission.name", target = "missionName")
  @Mapping(target = "owningSquadron", source = "owningOrgUnit")
  InventoryItemDto toDto(InventoryItem inventoryItem);

  /** Nested mapping for the item's {@link Location} (used as {@code uses} target). */
  LocationDto locationToDto(Location location);
}
