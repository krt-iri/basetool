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

import de.greluc.krt.profit.basetool.backend.model.Location;
import de.greluc.krt.profit.basetool.backend.model.Manufacturer;
import de.greluc.krt.profit.basetool.backend.model.Ship;
import de.greluc.krt.profit.basetool.backend.model.ShipType;
import de.greluc.krt.profit.basetool.backend.model.dto.LocationDto;
import de.greluc.krt.profit.basetool.backend.model.dto.ManufacturerDto;
import de.greluc.krt.profit.basetool.backend.model.dto.ShipDto;
import de.greluc.krt.profit.basetool.backend.model.dto.ShipTypeDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between Ship entities and DTOs. */
@Mapper(
    config = CentralMapperConfig.class,
    uses = {UserMapper.class, SquadronMapper.class})
public interface ShipMapper {
  /**
   * Maps a {@link Ship} entity to its outbound DTO.
   *
   * <p>After R9 Step 2 the ship entity exposes {@code owningOrgUnit} (typed {@code OrgUnit}); the
   * DTO still publishes {@code owningSquadron} as {@code SquadronReferenceDto} for API stability.
   * The explicit mapping routes the source through {@code SquadronMapper.orgUnitToReferenceDto},
   * which projects either kind — a Staffel or a Spezialkommando — into the slim owner reference
   * (id/name/shorthand), so SK-owned ships now surface their SK badge instead of a blank cell.
   *
   * @param ship the ship entity to project; {@code null} returns {@code null}.
   * @return the populated ship DTO.
   */
  @Mapping(target = "owningSquadron", source = "owningOrgUnit")
  ShipDto toDto(Ship ship);

  /** Nested mapping for the ship's stationing {@link Location}. */
  LocationDto locationToDto(Location location);

  /** Nested mapping for the ship's {@link Manufacturer}. */
  ManufacturerDto manufacturerToDto(Manufacturer manufacturer);

  /**
   * Lightweight nested mapping for the ship's {@link ShipType}. Intentionally narrower than the
   * full UEX-sourced ShipType view to avoid leaking irrelevant fields through the {@link Ship}
   * boundary.
   *
   * <p>R9 Step 2: the {@code description} wire field is sourced from the rich {@code descriptionDe}
   * / {@code descriptionEn} columns (German preferred, English fallback), not the legacy
   * synthesised {@code ship_type.description} column — which is no longer written and is dropped in
   * R9 Step 4.
   */
  @Mapping(
      target = "description",
      expression =
          "java(shipType.getDescriptionDe() != null ? shipType.getDescriptionDe()"
              + " : shipType.getDescriptionEn())")
  ShipTypeDto shipTypeToDto(ShipType shipType);
}
