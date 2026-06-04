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

package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.exception.EntityInUseException;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

  @Mock private LocationRepository locationRepository;

  @Mock private ShipRepository shipRepository;

  @Mock private RefineryOrderRepository refineryOrderRepository;

  @InjectMocks private LocationService locationService;

  @Test
  void createLocation_ShouldSaveDirectly() {
    // Given
    Location inputLocation = new Location();
    inputLocation.setName("Port Olisar");

    when(locationRepository.existsByNameIgnoreCase("Port Olisar")).thenReturn(false);
    when(locationRepository.save(any(Location.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    Location savedLocation = locationService.createLocation(inputLocation);

    // Then
    assertNotNull(savedLocation);
    assertEquals("Port Olisar", savedLocation.getName());
    verify(locationRepository, times(1)).save(inputLocation);
  }

  @Test
  void createLocation_DuplicateName_ShouldThrowException() {
    Location inputLocation = new Location();
    inputLocation.setName("Dupe");

    when(locationRepository.existsByNameIgnoreCase("Dupe")).thenReturn(true);

    assertThrows(
        DuplicateEntityException.class, () -> locationService.createLocation(inputLocation));
  }

  @Test
  void deleteLocation_WhenInUseByShips_ShouldThrowException() {
    UUID locId = UUID.randomUUID();
    when(shipRepository.existsByLocationId(locId)).thenReturn(true);

    assertThrows(EntityInUseException.class, () -> locationService.deleteLocation(locId));
  }

  @Test
  void deleteLocation_WhenInUseByRefineryOrders_ShouldThrowException() {
    UUID locId = UUID.randomUUID();
    when(shipRepository.existsByLocationId(locId)).thenReturn(false);
    when(refineryOrderRepository.existsByLocationId(locId)).thenReturn(true);

    assertThrows(EntityInUseException.class, () -> locationService.deleteLocation(locId));
  }
}
