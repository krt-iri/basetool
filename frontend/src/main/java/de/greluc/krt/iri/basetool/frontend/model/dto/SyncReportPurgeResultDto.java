package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Frontend mirror of the backend {@code SyncReportPurgeResultDto}. Lets {@code
 * AdminSyncReportsPageController} deserialise the response of the "delete reports older than X
 * days" action without depending on the backend module.
 *
 * <p>Fields and types must stay in lockstep with {@code
 * de.greluc.krt.iri.basetool.backend.model.dto.SyncReportPurgeResultDto} — any backend change
 * requires a matching change here in the same commit (mirror-DTO rule).
 *
 * @param deleted number of sync-report rows deleted by the purge
 */
public record SyncReportPurgeResultDto(int deleted) {}
