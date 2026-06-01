package de.greluc.krt.iri.basetool.backend.model.dto;

/**
 * Response DTO for the admin "delete reports older than X days" action on {@code
 * /api/v1/sync-reports}. Reports how many {@code external_sync_report} rows the purge removed.
 *
 * @param deleted number of sync-report rows deleted by the purge (0 when nothing matched)
 */
public record SyncReportPurgeResultDto(int deleted) {}
