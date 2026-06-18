/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.backend.mapper;

import de.greluc.krt.profit.basetool.backend.model.InventoryItem;
import de.greluc.krt.profit.basetool.backend.model.Location;
import de.greluc.krt.profit.basetool.backend.model.dto.InventoryItemDto;
import de.greluc.krt.profit.basetool.backend.model.dto.LocationDto;
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
   * SquadronMapper.orgUnitToReferenceDto}, which projects either kind — a Staffel or a
   * Spezialkommando — into the slim owner reference (id/name/shorthand), so SK-owned stock now
   * surfaces its SK badge instead of a blank cell.
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
