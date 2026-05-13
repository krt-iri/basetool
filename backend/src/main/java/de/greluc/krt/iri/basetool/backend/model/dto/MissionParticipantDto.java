package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PayoutPreference;
import java.time.Instant;
import java.util.UUID;

public record MissionParticipantDto(
    UUID id,
    UserDto user,
    String guestName,
    SquadronDto squadron,
    JobTypeDto desiredMissionJobType,
    JobTypeDto plannedMissionJobType,
    String comment,
    Instant startTime,
    Instant endTime,
    PayoutPreference payoutPreference,
    Long version) {}
