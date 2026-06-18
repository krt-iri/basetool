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

package de.greluc.krt.profit.basetool.backend.service;

import de.greluc.krt.profit.basetool.backend.mapper.MissionMapper;
import de.greluc.krt.profit.basetool.backend.mapper.RefineryOrderMapper;
import de.greluc.krt.profit.basetool.backend.model.FinanceType;
import de.greluc.krt.profit.basetool.backend.model.Mission;
import de.greluc.krt.profit.basetool.backend.model.MissionFinanceEntry;
import de.greluc.krt.profit.basetool.backend.model.Operation;
import de.greluc.krt.profit.basetool.backend.model.RefineryOrder;
import de.greluc.krt.profit.basetool.backend.model.dto.MissionFinanceEntryDto;
import de.greluc.krt.profit.basetool.backend.model.dto.MissionFinanceSummaryDto;
import de.greluc.krt.profit.basetool.backend.model.dto.OperationFinanceDto;
import de.greluc.krt.profit.basetool.backend.model.dto.RefineryOrderDto;
import de.greluc.krt.profit.basetool.backend.repository.MissionFinanceEntryRepository;
import de.greluc.krt.profit.basetool.backend.repository.OperationRepository;
import de.greluc.krt.profit.basetool.backend.repository.RefineryOrderRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates finance data across all missions that belong to an operation.
 *
 * <p>The operation level is purely a roll-up of its missions: per-mission finance entries (income
 * vs expense) plus the profit/loss contribution of refinery orders linked to those missions (ore
 * sales minus expenses and other expenses). Returns a structured DTO that the frontend turns into a
 * per-mission breakdown plus an operation-wide total.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperationFinanceService {

  private final OperationRepository operationRepository;
  private final MissionFinanceEntryRepository financeEntryRepository;
  private final RefineryOrderRepository refineryOrderRepository;
  private final MissionMapper missionMapper;
  private final RefineryOrderMapper refineryOrderMapper;

  /**
   * Builds the aggregated finance DTO for the operation.
   *
   * <p>Loads the operation and groups all its missions' finance entries and refinery orders in
   * memory rather than firing one query per mission — for a typical 5-mission operation this cuts
   * the number of round trips from 1+2N (one for entries, one for refinery orders per mission) to 3
   * fixed.
   *
   * @param operationId operation primary key
   * @return aggregated finance summary
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when no operation
   *     matches the id
   */
  public OperationFinanceDto getOperationFinances(UUID operationId) {
    Operation operation =
        operationRepository
            .findById(operationId)
            .orElseThrow(
                () ->
                    new de.greluc.krt.profit.basetool.backend.exception.NotFoundException(
                        "Operation not found"));

    List<UUID> missionIds = operation.getMissions().stream().map(Mission::getId).toList();

    if (missionIds.isEmpty()) {
      return new OperationFinanceDto(operationId, BigDecimal.ZERO, List.of());
    }

    List<MissionFinanceEntry> allEntries = financeEntryRepository.findAllByMissionIdIn(missionIds);
    List<RefineryOrder> allRefineryOrders = refineryOrderRepository.findByMissionIdIn(missionIds);

    Map<UUID, List<MissionFinanceEntry>> entriesByMission =
        allEntries.stream().collect(Collectors.groupingBy(entry -> entry.getMission().getId()));

    Map<UUID, List<RefineryOrder>> refineryOrdersByMission =
        allRefineryOrders.stream()
            .filter(order -> order.getMission() != null)
            .collect(Collectors.groupingBy(order -> order.getMission().getId()));

    BigDecimal operationTotalSum = BigDecimal.ZERO;
    List<MissionFinanceSummaryDto> missionSummaries = new ArrayList<>();

    for (Mission mission : operation.getMissions()) {
      List<MissionFinanceEntry> entries = entriesByMission.getOrDefault(mission.getId(), List.of());
      List<RefineryOrder> orders = refineryOrdersByMission.getOrDefault(mission.getId(), List.of());

      BigDecimal missionTotalSum = BigDecimal.ZERO;

      for (MissionFinanceEntry entry : entries) {
        if (entry.getType() == FinanceType.INCOME) {
          missionTotalSum = missionTotalSum.add(entry.getAmount());
        } else if (entry.getType() == FinanceType.EXPENSE) {
          missionTotalSum = missionTotalSum.subtract(entry.getAmount());
        }
      }

      // Refinery orders contribute their profit/loss (oreSales - expenses - otherExpenses)
      // rather than only their costs. Legacy data with null values is treated as 0.
      for (RefineryOrder order : orders) {
        double sales = order.getOreSales() != null ? order.getOreSales() : 0d;
        double costs = order.getExpenses() != null ? order.getExpenses() : 0d;
        double otherCosts = order.getOtherExpenses() != null ? order.getOtherExpenses() : 0d;
        double profit = sales - costs - otherCosts;
        if (profit != 0d) {
          missionTotalSum = missionTotalSum.add(BigDecimal.valueOf(profit));
        }
      }

      operationTotalSum = operationTotalSum.add(missionTotalSum);

      List<MissionFinanceEntryDto> entryDtos = entries.stream().map(missionMapper::toDto).toList();

      List<RefineryOrderDto> orderDtos = orders.stream().map(refineryOrderMapper::toDto).toList();

      missionSummaries.add(
          new MissionFinanceSummaryDto(
              mission.getId(), mission.getName(), missionTotalSum, entryDtos, orderDtos));
    }

    return new OperationFinanceDto(operationId, operationTotalSum, missionSummaries);
  }
}
