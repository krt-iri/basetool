package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.MissionMapper;
import de.greluc.krt.iri.basetool.backend.mapper.RefineryOrderMapper;
import de.greluc.krt.iri.basetool.backend.model.FinanceType;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionFinanceEntry;
import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionFinanceEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionFinanceSummaryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationFinanceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderDto;
import de.greluc.krt.iri.basetool.backend.repository.MissionFinanceEntryRepository;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationFinanceService {

    private final OperationRepository operationRepository;
    private final MissionFinanceEntryRepository financeEntryRepository;
    private final RefineryOrderRepository refineryOrderRepository;
    private final MissionMapper missionMapper;
    private final RefineryOrderMapper refineryOrderMapper;

    @Transactional(readOnly = true)
    public OperationFinanceDto getOperationFinances(UUID operationId) {
        Operation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new RuntimeException("Operation not found"));

        List<UUID> missionIds = operation.getMissions().stream()
                .map(Mission::getId)
                .toList();

        if (missionIds.isEmpty()) {
            return new OperationFinanceDto(operationId, BigDecimal.ZERO, List.of());
        }

        List<MissionFinanceEntry> allEntries = financeEntryRepository.findAllByMissionIdIn(missionIds);
        List<RefineryOrder> allRefineryOrders = refineryOrderRepository.findByMissionIdIn(missionIds);

        Map<UUID, List<MissionFinanceEntry>> entriesByMission = allEntries.stream()
                .collect(Collectors.groupingBy(entry -> entry.getMission().getId()));

        Map<UUID, List<RefineryOrder>> refineryOrdersByMission = allRefineryOrders.stream()
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

            // Raffinerieauftraege fliessen mit ihrem Gewinn/Verlust (oreSales - expenses) ein,
            // nicht nur mit ihren Kosten. Altdaten mit oreSales=null werden als 0 behandelt.
            for (RefineryOrder order : orders) {
                double sales = order.getOreSales() != null ? order.getOreSales() : 0d;
                double costs = order.getExpenses() != null ? order.getExpenses() : 0d;
                double profit = sales - costs;
                if (profit != 0d) {
                    missionTotalSum = missionTotalSum.add(BigDecimal.valueOf(profit));
                }
            }

            operationTotalSum = operationTotalSum.add(missionTotalSum);

            List<MissionFinanceEntryDto> entryDtos = entries.stream()
                    .map(missionMapper::toDto)
                    .toList();

            List<RefineryOrderDto> orderDtos = orders.stream()
                    .map(refineryOrderMapper::toDto)
                    .toList();

            missionSummaries.add(new MissionFinanceSummaryDto(
                    mission.getId(),
                    mission.getName(),
                    missionTotalSum,
                    entryDtos,
                    orderDtos
            ));
        }

        return new OperationFinanceDto(operationId, operationTotalSum, missionSummaries);
    }
}