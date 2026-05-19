package de.greluc.krt.iri.basetool.frontend.model.dto;

import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Data transfer record carrying Refinery Order payload. */
public record RefineryOrderDto(
    UUID id,
    UserReferenceDto owner,
    LocationDto location,
    MissionReferenceDto mission,
    Instant startedAt,
    @Positive Long durationMinutes,
    @Positive Double expenses,
    Double otherExpenses,
    Double oreSales,
    Double profit,
    RefiningMethodDto refiningMethod,
    List<RefineryGoodDto> goods,
    RefineryOrderStatus status,
    SquadronReferenceDto owningSquadron,
    Long version) {
  /**
   * Derived end timestamp ({@code startedAt + durationMinutes}); {@code null} if either is unset.
   */
  public Instant getEndsAt() {
    if (startedAt != null && durationMinutes != null) {
      return startedAt.plus(durationMinutes, java.time.temporal.ChronoUnit.MINUTES);
    }
    return null;
  }
}
