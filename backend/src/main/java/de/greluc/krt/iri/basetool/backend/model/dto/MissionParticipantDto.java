package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PayoutPreference;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data transfer record carrying Mission Participant payload. The {@code orgUnits} list carries the
 * participant's affiliations (zero, one, or several org units — a Staffel and/or Spezialkommandos),
 * replacing the former single {@code squadron} field so a member of both a Staffel and an SK has
 * both badges rendered on the roster.
 */
public record MissionParticipantDto(
    UUID id,
    UserDto user,
    String guestName,
    List<OrgUnitReferenceDto> orgUnits,
    JobTypeDto desiredMissionJobType,
    JobTypeDto plannedMissionJobType,
    String comment,
    Instant startTime,
    Instant endTime,
    PayoutPreference payoutPreference,
    Long version) {}
