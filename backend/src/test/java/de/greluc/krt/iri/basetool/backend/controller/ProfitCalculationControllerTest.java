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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.model.dto.ProfitCalculationDto;
import de.greluc.krt.iri.basetool.backend.service.ProfitCalculationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfitCalculationControllerTest {

  @Mock private ProfitCalculationService profitCalculationService;

  @InjectMocks private ProfitCalculationController profitCalculationController;

  @Test
  void shouldGetProfitCalculation() {
    // Given
    UUID shipId = UUID.randomUUID();
    List<String> systems = List.of("Stanton");
    ProfitCalculationDto dto =
        new ProfitCalculationDto(
            UUID.randomUUID(), "Laranite",
            BigDecimal.valueOf(20), BigDecimal.valueOf(30),
            BigDecimal.valueOf(10), BigDecimal.valueOf(50),
            BigDecimal.valueOf(160), BigDecimal.valueOf(80));

    when(profitCalculationService.calculateProfit(shipId, systems)).thenReturn(List.of(dto));

    // When
    List<ProfitCalculationDto> result =
        profitCalculationController.getProfitCalculation(shipId, systems);

    // Then
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("Laranite", result.get(0).materialName());
    verify(profitCalculationService, times(1)).calculateProfit(shipId, systems);
  }
}
