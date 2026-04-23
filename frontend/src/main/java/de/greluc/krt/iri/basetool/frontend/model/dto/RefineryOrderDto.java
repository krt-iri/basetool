package de.greluc.krt.iri.basetool.frontend.model.dto;

import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record RefineryOrderDto(
        UUID id,
        UserReferenceDto owner,
        LocationDto location,
        MissionReferenceDto mission,
        Instant startedAt,
        @Positive Long durationMinutes,
        @Positive Double expenses,
        Double oreSales,
        Double profit,
        RefiningMethodDto refiningMethod,
        List<RefineryGoodDto> goods,
        RefineryOrderStatus status,
        Long version
) {
    public Instant getEndsAt() {
        if (startedAt != null && durationMinutes != null) {
            return startedAt.plus(durationMinutes, java.time.temporal.ChronoUnit.MINUTES);
        }
        return null;
    }
}