package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Frontend mirror of the backend's {@code CreateMissionRequest} write-only DTO at {@code POST
 * /api/v1/missions}. Separate from {@link MissionDto} so the create flow does not have to thread
 * dozens of read-only fields (sub-missions, participants, inventory, version counters) through a
 * null-filled constructor every time.
 *
 * <p>R5.d.d added the trailing {@link #owningOrgUnitId} picker output — the backend's {@code
 * OwnerScopeService.resolveSquadronForPickerOutput} validates it against the caller's memberships
 * and rejects Spezialkommando selections with 400 until the destructive cleanup release loosens NOT
 * NULL on the legacy {@code owning_squadron_id} column.
 */
public record CreateMissionRequest(
    String name,
    String description,
    String calendarLink,
    String status,
    Instant meetingTime,
    Instant plannedStartTime,
    Instant plannedEndTime,
    Boolean isInternal,
    UUID operationId,
    UUID owningOrgUnitId) {}
