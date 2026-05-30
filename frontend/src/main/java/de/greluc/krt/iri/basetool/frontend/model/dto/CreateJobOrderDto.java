package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Frontend mirror of the backend's {@code CreateJobOrderDto}. Carries the picker output for the Job
 * Order create form (R5.d.c): the {@link #requestingOrgUnitId} field replaces the historical {@code
 * requestingSquadronId} so the form picker can offer Staffel + Spezialkommando alike; today SK
 * selections are rejected with 400 by the backend until the destructive cleanup release lowers NOT
 * NULL on the legacy {@code requesting_squadron_id} column. The {@code creatingSquadronId} admin
 * override stays untouched in R5.d.c — its plan-aligned rename to {@code creatingOrgUnitId} ships
 * in a follow-up.
 */
public record CreateJobOrderDto(
    @Nullable UUID creatingSquadronId,
    @Nullable UUID requestingOrgUnitId,
    String handle,
    String comment,
    List<CreateJobOrderMaterialDto> materials,
    Long version) {}
