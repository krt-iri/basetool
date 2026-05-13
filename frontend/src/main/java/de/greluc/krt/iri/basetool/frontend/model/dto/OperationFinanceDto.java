package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OperationFinanceDto(
    UUID operationId, BigDecimal totalSum, List<MissionFinanceSummaryDto> missions) {}
