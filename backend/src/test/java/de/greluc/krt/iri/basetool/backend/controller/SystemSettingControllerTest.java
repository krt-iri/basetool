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

package de.greluc.krt.iri.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.model.dto.SystemSettingDto;
import de.greluc.krt.iri.basetool.backend.model.dto.SystemSettingUpdateDto;
import de.greluc.krt.iri.basetool.backend.service.SystemSettingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure-method unit tests for {@link SystemSettingController}. The Spring-MVC binding (path params,
 * request-body validation, {@code @PreAuthorize}) is covered by the integration test suite; here we
 * only verify the controller-to-service delegation. A regression here means the wrong key gets
 * persisted (the only real failure mode the controller has).
 */
@ExtendWith(MockitoExtension.class)
class SystemSettingControllerTest {

  @Mock private SystemSettingService systemSettingService;

  @InjectMocks private SystemSettingController controller;

  @Test
  void getAllSettings_delegatesToService_andReturnsList() {
    // Given
    List<SystemSettingDto> serviceResponse =
        List.of(
            new SystemSettingDto("feature.flag.combat", "ENABLED", 1L),
            new SystemSettingDto("feature.flag.refinery", "DISABLED", 3L));
    when(systemSettingService.getAllSettings()).thenReturn(serviceResponse);

    // When
    List<SystemSettingDto> result = controller.getAllSettings();

    // Then
    assertEquals(serviceResponse, result);
    verify(systemSettingService).getAllSettings();
    verifyNoMoreInteractions(systemSettingService);
  }

  @Test
  void getSetting_passesKeyThroughVerbatim() {
    // Given
    SystemSettingDto expected = new SystemSettingDto("feature.flag.combat", "ENABLED", 1L);
    when(systemSettingService.getSetting("feature.flag.combat")).thenReturn(expected);

    // When
    SystemSettingDto result = controller.getSetting("feature.flag.combat");

    // Then
    assertSame(expected, result);
    // The controller MUST NOT transform / lowercase / strip the key — the
    // service does its own validation and stores it verbatim. A regression
    // here would silently fork the same setting into two rows.
    verify(systemSettingService).getSetting("feature.flag.combat");
  }

  @Test
  void updateSetting_passesKeyAndDtoToService_andReturnsResult() {
    // Given
    SystemSettingUpdateDto update = new SystemSettingUpdateDto("DISABLED", 5L);
    SystemSettingDto persisted = new SystemSettingDto("feature.flag.refinery", "DISABLED", 6L);
    when(systemSettingService.updateSetting("feature.flag.refinery", update)).thenReturn(persisted);

    // When
    SystemSettingDto result = controller.updateSetting("feature.flag.refinery", update);

    // Then
    assertSame(persisted, result);
    verify(systemSettingService).updateSetting("feature.flag.refinery", update);
  }
}
