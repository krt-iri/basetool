package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Data transfer record carrying Mission Finance Summary payload. */
public record MissionFinanceSummaryDto(
    UUID missionId,
    String missionName,
    BigDecimal totalSum,
    List<MissionFinanceEntryDto> entries,
    List<RefineryOrderDto> refineryOrders) {}
