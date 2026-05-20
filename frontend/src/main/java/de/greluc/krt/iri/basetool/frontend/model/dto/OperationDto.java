package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Data transfer record carrying Operation payload — frontend mirror of the backend record.
 *
 * <p>{@code payoutPreliminary} is populated by the backend only on the {@code GET
 * /api/v1/operations/{id}} detail endpoint and is {@code null} otherwise. The operation-detail
 * Thymeleaf template reads it to render the "payout figures are preliminary" warning banner above
 * the payout table when at least one mission of the operation still lacks an actual start or end
 * time. Treat {@code null} as "unknown" and hide the banner.
 *
 * @param id operation primary key
 * @param name operation name
 * @param description optional free-text description
 * @param status current operation status (string mirror of the backend enum)
 * @param owningSquadron squadron that owns the operation (multi-tenant scope marker)
 * @param version optimistic-lock version
 * @param payoutPreliminary {@code true} when the backend reports that at least one mission has no
 *     {@code actualStartTime} or {@code actualEndTime}; {@code null} when not computed
 */
public record OperationDto(
    UUID id,
    String name,
    String description,
    String status,
    SquadronReferenceDto owningSquadron,
    Long version,
    Boolean payoutPreliminary) {}
