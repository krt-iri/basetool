package de.greluc.krt.iri.basetool.frontend.model.dto;

import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Frontend mirror of the backend {@code RefineryOrderDto} wire shape (per the {@code
 * feedback_backend_frontend_dto_mirror} memory: backend + frontend records must stay aligned
 * field-for-field, or a render-time 500 surfaces in prod).
 *
 * <p>The trailing {@code owningOrgUnitId} field is the R5.d picker output sent to the backend on
 * create; {@code null} preserves the legacy "owner's home Staffel" stamping path.
 */
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
    Long version,
    UUID owningOrgUnitId) {
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
