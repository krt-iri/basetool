package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Frontend mirror of the backend's {@code JobOrderHandoverDto}. Carries the executing-user +
 * squadron audit snapshot so handover history rows can show "who performed it" alongside the
 * recipient details (MULTI_SQUADRON_PLAN.md section 4.4). Both fields stay {@code null} for
 * historical rows that pre-date the audit columns.
 */
public record JobOrderHandoverDto(
    UUID id,
    UUID jobOrderId,
    Instant handoverTime,
    String recipientHandle,
    String recipientSquadron,
    @Nullable UserReferenceDto executingUser,
    @Nullable SquadronReferenceDto executingSquadron,
    List<JobOrderHandoverItemDto> items,
    Long version) {}
