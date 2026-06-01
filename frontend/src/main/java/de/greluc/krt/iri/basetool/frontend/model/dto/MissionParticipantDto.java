package de.greluc.krt.iri.basetool.frontend.model.dto;

import de.greluc.krt.iri.basetool.frontend.model.PayoutPreference;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Data transfer record carrying Mission Participant payload. The {@code orgUnits} list carries the
 * participant's affiliations (zero, one, or several org units — a Staffel and/or Spezialkommandos),
 * mirroring the backend DTO so the roster renders an org-unit badge per affiliation.
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
    Long version) {
  public String getStartTimeFormatted() {
    return formatInstant(startTime);
  }

  public String getEndTimeFormatted() {
    return formatInstant(endTime);
  }

  private String formatInstant(Instant instant) {
    if (instant == null) {
      return "";
    }
    return instant.atZone(ZoneId.of("Europe/Berlin")).toLocalDateTime().toString();
  }
}
