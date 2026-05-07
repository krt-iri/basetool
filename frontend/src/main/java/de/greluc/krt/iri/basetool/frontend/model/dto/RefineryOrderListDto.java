package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RefineryOrderListDto(
        UUID id,
        UserReferenceDto owner,
        LocationDto location,
        MissionReferenceDto mission,
        Instant startedAt,
        Long durationMinutes,
        Double expenses,
        Double otherExpenses,
        Double oreSales,
        Double profit,
        RefiningMethodDto refiningMethod,
        String status,
        List<RefineryGoodDto> goods,
        Long version
) {
    public Instant getEndsAt() {
        if (startedAt != null && durationMinutes != null) {
            return startedAt.plus(durationMinutes, java.time.temporal.ChronoUnit.MINUTES);
        }
        return null;
    }
}
