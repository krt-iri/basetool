package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Data transfer record carrying Refinery Order payload. */
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
    Long version) {}
