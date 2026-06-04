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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.greluc.krt.iri.basetool.backend.model.City;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.SpaceStation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class LocationRepositoryRefineryTest {

  @Autowired private LocationRepository locationRepository;

  @Autowired private CityRepository cityRepository;

  @Autowired private SpaceStationRepository spaceStationRepository;

  @Test
  public void testFindLocationsWithRefinery() {
    City city = new City();
    city.setName("Refinery City");
    city.setHasRefinery(true);
    cityRepository.save(city);

    Location loc1 = new Location();
    loc1.setName("Refinery City Loc");
    loc1.setCity(city);
    locationRepository.save(loc1);

    SpaceStation ss = new SpaceStation();
    ss.setName("Refinery Station");
    ss.setHasRefinery(true);
    spaceStationRepository.save(ss);

    Location loc2 = new Location();
    loc2.setName("Refinery Station Loc");
    loc2.setSpaceStation(ss);
    locationRepository.save(loc2);

    City city2 = new City();
    city2.setName("Normal City");
    city2.setHasRefinery(false);
    cityRepository.save(city2);

    Location loc3 = new Location();
    loc3.setName("Normal City Loc");
    loc3.setCity(city2);
    locationRepository.save(loc3);

    locationRepository.flush();

    List<Location> refineries = locationRepository.findLocationsWithRefinery();

    assertTrue(refineries.contains(loc1));
    assertTrue(refineries.contains(loc2));
    assertFalse(refineries.contains(loc3));
  }
}
