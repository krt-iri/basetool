package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Data transfer record carrying mission payload.
 *
 * <p>{@code version} is the global mission counter (legacy full-update path). The dedicated section
 * counters {@code coreVersion}, {@code scheduleVersion} and {@code flagsVersion} drive the
 * section-scoped patch endpoints; carrying them on the frontend DTO lets the mission detail page
 * pin the correct counter into each hidden form input independently.
 */
public record MissionDto(
    UUID id,
    String name,
    String description,
    String calendarLink,
    String status,
    Instant meetingTime,
    Instant plannedStartTime,
    Instant actualStartTime,
    Instant plannedEndTime,
    Instant actualEndTime,
    Boolean isInternal,
    Set<MissionParticipantDto> participants,
    List<MissionUnitDto> assignedUnits,
    List<MissionFrequencyDto> frequencies,
    Set<MissionDto> subMissions,
    List<InventoryItemDto> inventoryEntries,
    List<RefineryOrderDto> refineryOrders,
    OperationDto operation,
    UserReferenceDto owner,
    Set<UserReferenceDto> managers,
    Boolean canEdit,
    Boolean canManageManagers,
    Long version,
    Long coreVersion,
    Long scheduleVersion,
    Long flagsVersion,
    Integer checkedInParticipants,
    Integer registeredParticipants) {}
