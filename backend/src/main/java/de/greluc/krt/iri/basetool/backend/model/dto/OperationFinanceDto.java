package de.greluc.krt.iri.basetool.backend.model.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Data transfer record carrying Operation Finance payload. */
public record OperationFinanceDto(
    UUID operationId, BigDecimal totalSum, List<MissionFinanceSummaryDto> missions) {}
