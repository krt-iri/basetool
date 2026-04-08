package de.greluc.krt.iri.basetool.frontend.model.dto;

import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;

public record RefineryOrderDto(
        UUID id,
        UserReferenceDto owner,
        LocationDto location,
        MissionReferenceDto mission,
        OffsetDateTime startedAt,
        @Positive Integer durationMinutes,
        @Positive Integer expenses,
        RefiningMethodDto refiningMethod,
        List<RefineryGoodDto> goods,
        RefineryOrderStatus status,
        Long version
) {
    public OffsetDateTime getEndsAt() {
        if (startedAt != null && durationMinutes != null) {
            return startedAt.plusMinutes(durationMinutes);
        }
        return null;
    }
}