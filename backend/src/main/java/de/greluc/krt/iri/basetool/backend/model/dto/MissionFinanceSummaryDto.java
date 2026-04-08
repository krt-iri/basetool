package de.greluc.krt.iri.basetool.backend.model.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MissionFinanceSummaryDto(
        UUID missionId,
        String missionName,
        BigDecimal totalSum,
        List<MissionFinanceEntryDto> entries,
        List<RefineryOrderDto> refineryOrders
) {
}
