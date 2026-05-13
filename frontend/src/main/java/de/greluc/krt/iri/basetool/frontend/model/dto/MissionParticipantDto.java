package de.greluc.krt.iri.basetool.frontend.model.dto;

import de.greluc.krt.iri.basetool.frontend.model.PayoutPreference;
import java.time.Instant;
import java.time.ZoneId;
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
    Long version) {
  public String getStartTimeFormatted() {
    return formatInstant(startTime);
  }

  public String getEndTimeFormatted() {
    return formatInstant(endTime);
  }

  private String formatInstant(Instant instant) {
    if (instant == null) return "";
    return instant.atZone(ZoneId.of("Europe/Berlin")).toLocalDateTime().toString();
  }
}
