package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Frontend mirror of the backend's {@code CreateJobOrderDto}. Carries the picker output for the Job
 * Order create form: {@link #responsibleOrgUnitId} is the profit-eligible org unit that processes
 * the order (required for authenticated callers, ignored for guests who are routed to the intake
 * SK), and {@link #requestingOrgUnitId} is the customer (any squadron or SK). Both accept Staffel +
 * Spezialkommando.
 */
public record CreateJobOrderDto(
    @Nullable UUID responsibleOrgUnitId,
    @Nullable UUID requestingOrgUnitId,
    String handle,
    String comment,
    List<CreateJobOrderMaterialDto> materials,
    Long version) {}
