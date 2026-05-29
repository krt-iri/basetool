package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Frontend mirror of the backend {@code SyncReportDto}. Lives in the frontend module so {@code
 * AdminSyncReportsPageController} can deserialise the REST response without depending on the
 * backend module.
 *
 * <p>Fields and types must stay in lockstep with {@code
 * de.greluc.krt.iri.basetool.backend.model.dto.SyncReportDto} — any backend change requires a
 * matching change here in the same commit (mirror-DTO rule).
 *
 * @param id event id
 * @param runId run id grouping a sync cycle's events
 * @param ranAt event timestamp (UTC)
 * @param sourceSystem catalogue name ({@code "UEX"} or {@code "SCWIKI"})
 * @param eventType event-type name
 * @param aggregate aggregate label
 * @param externalUuid external asset UUID, or {@code null}
 * @param externalId external integer id, or {@code null}
 * @param externalName external display name, or {@code null}
 * @param detail free-form detail
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
