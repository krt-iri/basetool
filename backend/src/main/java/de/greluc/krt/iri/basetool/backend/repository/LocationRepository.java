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

package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Location;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Location. */
@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {

  /**
   * Returns slim {@code LocationReferenceDto}s (id + name) for every non-hidden location, ordered
   * by name. Used to populate location pickers without pulling the full Location aggregate.
   */
  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.dto.LocationReferenceDto(l.id, l.name)"
          + " FROM Location l WHERE l.hidden = false ORDER BY l.name")
  List<de.greluc.krt.iri.basetool.backend.model.dto.LocationReferenceDto> findAllReference();

  /** Derived Spring-Data query - returns entities matching {@code Name}. */
  Optional<Location> findByName(String name);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * NameIgnoreCase}.
   */
  boolean existsByNameIgnoreCase(String name);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * NameIgnoreCaseAndIdNot}.
   */
  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

  /** Derived Spring-Data query - returns entities matching {@code CityId}. */
  Optional<Location> findByCityId(UUID cityId);

  /** Derived Spring-Data query - returns entities matching {@code SpaceStationId}. */
  Optional<Location> findBySpaceStationId(UUID spaceStationId);

  /** Derived Spring-Data query - returns entities matching {@code HiddenFalse}. */
  Page<Location> findByHiddenFalse(Pageable pageable);

  /**
   * Returns every location attached to a city or space-station that has a refinery; used as the
   * picker source when the user creates a refinery order.
   */
  @Query(
      "SELECT l FROM Location l LEFT JOIN l.city c LEFT JOIN l.spaceStation s WHERE c.hasRefinery ="
          + " true OR s.hasRefinery = true")
  List<Location> findLocationsWithRefinery();
}
