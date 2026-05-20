package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.OperationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer record carrying Operation payload.
 *
 * <p>{@code payoutPreliminary} is an authoritative {@link Boolean} only on the {@code GET
 * /api/v1/operations/{id}} detail endpoint, where the controller fills it from {@code
 * OperationService.hasUnfinishedMissions(id)}. On every other endpoint that emits this DTO (list /
 * create / update responses) the field is {@code null} because the question is detail-page-specific
 * and the bulk endpoints have no reason to spend the extra count query. Treat {@code null} as
 * "unknown" — UI consumers should default to hiding the preliminary-warning banner.
 *
 * @param id operation primary key
 * @param name operation name
 * @param description optional free-text description
 * @param status current operation status
 * @param owningSquadron squadron that owns the operation (multi-tenant scope marker)
 * @param version optimistic-lock version
 * @param createdAt creation timestamp (UTC)
 * @param updatedAt last-update timestamp (UTC)
 * @param payoutPreliminary {@code true} when at least one mission of the operation has no {@code
 *     actualStartTime} or no {@code actualEndTime} and the payout breakdown therefore may rebalance
 *     once every mission is closed; {@code false} when every mission has both timestamps; {@code
 *     null} when the flag was not computed (non-detail endpoints)
 */
public record OperationDto(
    UUID id,
    String name,
    String description,
    OperationStatus status,
    SquadronReferenceDto owningSquadron,
    Long version,
    Instant createdAt,
    Instant updatedAt,
    Boolean payoutPreliminary) {

  /**
   * Returns a copy of this DTO with {@code payoutPreliminary} set to {@code value}. Used by the
   * detail-endpoint controller to attach the freshly computed flag without re-running the full
   * mapper or fanning out a second mapping path through MapStruct.
   *
   * @param value the flag value to set on the returned copy
   * @return a new {@code OperationDto} identical to this one except for {@code payoutPreliminary}
   */
  public OperationDto withPayoutPreliminary(@org.jetbrains.annotations.Nullable Boolean value) {
    return new OperationDto(
        id, name, description, status, owningSquadron, version, createdAt, updatedAt, value);
  }
}
