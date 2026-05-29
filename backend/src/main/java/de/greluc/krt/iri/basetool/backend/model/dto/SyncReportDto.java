package de.greluc.krt.iri.basetool.backend.model.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for one {@link de.greluc.krt.iri.basetool.backend.model.ExternalSyncReport} row.
 *
 * <p>Read-only projection for the {@code /admin/sync-reports} pages. The {@code sourceSystem} and
 * {@code eventType} enums are flattened to their {@code String} names for the wire so the frontend
 * mirror need not depend on the backend enums.
 *
 * @param id event id
 * @param runId run id grouping a sync cycle's events
 * @param ranAt event timestamp (UTC)
 * @param sourceSystem catalogue name ({@code "UEX"} or {@code "SCWIKI"})
 * @param eventType event-type name (see {@code SyncEventType})
 * @param aggregate aggregate label ({@code "commodity"} / {@code "game_item"} / …)
 * @param externalUuid external asset UUID the event concerns, or {@code null}
 * @param externalId external integer id the event concerns, or {@code null}
 * @param externalName external display name the event concerns, or {@code null}
 * @param detail free-form human-readable detail
 */
public record SyncReportDto(
    UUID id,
    UUID runId,
    Instant ranAt,
    String sourceSystem,
    String eventType,
    String aggregate,
    UUID externalUuid,
    Integer externalId,
    String externalName,
    String detail) {}
