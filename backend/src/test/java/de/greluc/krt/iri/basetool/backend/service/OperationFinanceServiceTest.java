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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.mapper.MissionMapper;
import de.greluc.krt.iri.basetool.backend.mapper.RefineryOrderMapper;
import de.greluc.krt.iri.basetool.backend.model.*;
import de.greluc.krt.iri.basetool.backend.model.dto.*;
import de.greluc.krt.iri.basetool.backend.repository.MissionFinanceEntryRepository;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperationFinanceServiceTest {

  @Mock private OperationRepository operationRepository;

  @Mock private MissionFinanceEntryRepository financeEntryRepository;

  @Mock private RefineryOrderRepository refineryOrderRepository;

  @Mock private MissionMapper missionMapper;

  @Mock private RefineryOrderMapper refineryOrderMapper;

  @InjectMocks private OperationFinanceService operationFinanceService;

  @Test
  void shouldCalculateCorrectTotalSumWithFinancesAndRefineryOrders() {
    // Given
    UUID operationId = UUID.randomUUID();
    Operation operation = new Operation();
    operation.setId(operationId);

    Mission msn1 = new Mission();
    msn1.setId(UUID.randomUUID());
    msn1.setName("Mission 1");

    Mission msn2 = new Mission();
    msn2.setId(UUID.randomUUID());
    msn2.setName("Mission 2");

    operation.setMissions(Set.of(msn1, msn2));

    // Msn 1: 500 Income, 100 Expense -> +400
    MissionFinanceEntry e1 =
        MissionFinanceEntry.builder()
            .mission(msn1)
            .amount(BigDecimal.valueOf(500))
            .type(FinanceType.INCOME)
            .build();
    MissionFinanceEntry e2 =
        MissionFinanceEntry.builder()
            .mission(msn1)
            .amount(BigDecimal.valueOf(100))
            .type(FinanceType.EXPENSE)
            .build();

    // Msn 1: Refinery Order with 50 expense -> +350 total for msn1
    RefineryOrder ro1 = new RefineryOrder();
    ro1.setMission(msn1);
    ro1.setExpenses(50.0);

    // Msn 2: 200 Expense -> -200
    MissionFinanceEntry e3 =
        MissionFinanceEntry.builder()
            .mission(msn2)
            .amount(BigDecimal.valueOf(200))
            .type(FinanceType.EXPENSE)
            .build();

    // Operation Total -> 350 - 200 = 150

    when(operationRepository.findById(operationId)).thenReturn(Optional.of(operation));
    when(financeEntryRepository.findAllByMissionIdIn(any())).thenReturn(List.of(e1, e2, e3));
    when(refineryOrderRepository.findByMissionIdIn(any())).thenReturn(List.of(ro1));

    // When
    OperationFinanceDto result = operationFinanceService.getOperationFinances(operationId);

    // Then
    assertEquals(BigDecimal.valueOf(150.0), result.totalSum());
    assertEquals(2, result.missions().size());

    MissionFinanceSummaryDto sum1 =
        result.missions().stream()
            .filter(m -> m.missionId().equals(msn1.getId()))
            .findFirst()
            .orElseThrow();
    assertEquals(BigDecimal.valueOf(350.0), sum1.totalSum());

    MissionFinanceSummaryDto sum2 =
        result.missions().stream()
            .filter(m -> m.missionId().equals(msn2.getId()))
            .findFirst()
            .orElseThrow();
    assertEquals(BigDecimal.valueOf(-200), sum2.totalSum());
  }

  @Test
  void shouldUseProfitFromOreSalesMinusExpensesForRefineryOrders() {
    // Given: ein Raffinerieauftrag mit oreSales > expenses -> positiver Gewinn fliesst in die
    // Einsatzbilanz ein.
    UUID operationId = UUID.randomUUID();
    Operation operation = new Operation();
    operation.setId(operationId);

    Mission msn = new Mission();
    msn.setId(UUID.randomUUID());
    msn.setName("Profit Mission");
    operation.setMissions(Set.of(msn));

    RefineryOrder profitOrder = new RefineryOrder();
    profitOrder.setMission(msn);
    profitOrder.setExpenses(100.0);
    profitOrder.setOreSales(450.0); // profit = 350

    RefineryOrder lossOrder = new RefineryOrder();
    lossOrder.setMission(msn);
    lossOrder.setExpenses(200.0);
    lossOrder.setOreSales(50.0); // profit = -150

    RefineryOrder legacyOrder = new RefineryOrder();
    legacyOrder.setMission(msn);
    legacyOrder.setExpenses(25.0);
    legacyOrder.setOreSales(null); // Altdaten: oreSales=null -> 0, profit = -25

    when(operationRepository.findById(operationId)).thenReturn(Optional.of(operation));
    when(financeEntryRepository.findAllByMissionIdIn(any())).thenReturn(List.of());
    when(refineryOrderRepository.findByMissionIdIn(any()))
        .thenReturn(List.of(profitOrder, lossOrder, legacyOrder));

    // When
    OperationFinanceDto result = operationFinanceService.getOperationFinances(operationId);

    // Then: 350 + (-150) + (-25) = 175
    assertEquals(0, BigDecimal.valueOf(175.0).compareTo(result.totalSum()));
  }
}
