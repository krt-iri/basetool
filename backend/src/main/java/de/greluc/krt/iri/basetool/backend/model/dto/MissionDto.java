package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Data transfer record carrying Mission payload.
 *
 * <p>The {@code version} field is the global {@link
 * de.greluc.krt.iri.basetool.backend.model.AbstractEntity#getVersion()} counter and continues to
 * guard the legacy full-update endpoint. For the section-scoped patch endpoints ({@code /core},
 * {@code /schedule}, {@code /flags}) the dedicated counters {@code coreVersion}, {@code
 * scheduleVersion} and {@code flagsVersion} are the source of truth — they allow concurrent edits
 * on disjoint sections of the same mission without spurious 409 conflicts.
 *
 * <p>{@code owningSquadron} surfaces the mission's squadron ownership on the detail endpoint,
 * matching the column already exposed by {@link MissionListDto} on the list endpoint
 * (MULTI_SQUADRON_PLAN.md section 4.5: read DTOs of staffel-scoped aggregates carry the squadron
 * mini-record). May be {@code null} for historic rows persisted before V82.
 *
 * <p>{@code partyLeadUser} / {@code partyLeadGuestName} carry the mission's optional party lead
 * (Partyleiter): a registered user reference or a free-text handle, mutually exclusive (mirroring
 * the participant {@code user}/{@code guestName} duo). {@code partyLeadVersion} is the dedicated
 * section-scoped optimistic-lock counter the party-lead endpoint validates, in the same family as
 * {@code coreVersion}/{@code scheduleVersion}/{@code flagsVersion}.
 */
public record MissionDto(
    UUID id,
    @NotBlank String name,
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
    Integer registeredParticipants,
    SquadronReferenceDto owningSquadron,
    UserReferenceDto partyLeadUser,
    String partyLeadGuestName,
    Long partyLeadVersion) {}
