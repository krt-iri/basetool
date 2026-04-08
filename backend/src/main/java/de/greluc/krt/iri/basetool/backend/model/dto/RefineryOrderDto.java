package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RefineryOrderDto(
        UUID id,
        UserReferenceDto owner,
        @NotNull
        LocationDto location,
        MissionReferenceDto mission,
        Instant startedAt,
        Long durationMinutes,
        Double expenses,
        RefiningMethodDto refiningMethod,
        String status,
        @NotEmpty
        List<RefineryGoodDto> goods,
        Long version
) {
}
