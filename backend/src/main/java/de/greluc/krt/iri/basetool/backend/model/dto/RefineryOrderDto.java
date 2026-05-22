package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data transfer record carrying Refinery Order payload.
 *
 * <p>The trailing {@code owningOrgUnitId} field is the R5.d picker output: when present on create
 * (POST), the service stamps the new refinery order onto the picked org unit instead of the order
 * owner's home Staffel. {@code null} preserves the legacy stamping path. Validation against the
 * order owner's memberships happens at the service layer via {@code
 * OwnerScopeService.resolveSquadronForPickerOutput}.
 */
public record RefineryOrderDto(
    UUID id,
    UserReferenceDto owner,
    @NotNull LocationDto location,
    MissionReferenceDto mission,
    Instant startedAt,
    @PositiveOrZero Long durationMinutes,
    Double expenses,
    @PositiveOrZero Double otherExpenses,
    @PositiveOrZero Double oreSales,
    Double profit,
    RefiningMethodDto refiningMethod,
    String status,
    @NotEmpty List<RefineryGoodDto> goods,
    SquadronReferenceDto owningSquadron,
    Long version,
    UUID owningOrgUnitId) {}
