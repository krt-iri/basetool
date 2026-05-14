package de.greluc.krt.iri.basetool.backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies counting of checked-in and registered participants in {@link MissionMapper}. A
 * participant counts as "checked in" once their {@code startTime} is set.
 */
class MissionMapperParticipantCountTest {

  private final MissionMapper mapper = new MissionMapperImpl();

  @Test
  void shouldReturnZeroCounts_WhenNoParticipants() {
    // Given
    Mission mission = new Mission();
    mission.setParticipants(new HashSet<>());

    // When / Then
    assertEquals(0, mapper.resolveCheckedInParticipants(mission));
    assertEquals(0, mapper.resolveRegisteredParticipants(mission));
  }

  @Test
  void shouldCountAllRegisteredButNoneCheckedIn_WhenNobodyCheckedIn() {
    // Given
    Mission mission = new Mission();
    Set<MissionParticipant> participants = new HashSet<>();
    participants.add(buildParticipant(null));
    participants.add(buildParticipant(null));
    participants.add(buildParticipant(null));
    mission.setParticipants(participants);

    // When / Then
    assertEquals(0, mapper.resolveCheckedInParticipants(mission));
    assertEquals(3, mapper.resolveRegisteredParticipants(mission));
  }

  @Test
  void shouldCountAllAsCheckedIn_WhenEveryoneCheckedIn() {
    // Given
    Mission mission = new Mission();
    Set<MissionParticipant> participants = new HashSet<>();
    participants.add(buildParticipant(Instant.now()));
    participants.add(buildParticipant(Instant.now()));
    mission.setParticipants(participants);

    // When / Then
    assertEquals(2, mapper.resolveCheckedInParticipants(mission));
    assertEquals(2, mapper.resolveRegisteredParticipants(mission));
  }

  @Test
  void shouldCountMixed_WhenSomeCheckedIn() {
    // Given
    Mission mission = new Mission();
    Set<MissionParticipant> participants = new HashSet<>();
    participants.add(buildParticipant(Instant.now()));
    participants.add(buildParticipant(null));
    participants.add(buildParticipant(Instant.now()));
    participants.add(buildParticipant(null));
    mission.setParticipants(participants);

    // When / Then
    assertEquals(2, mapper.resolveCheckedInParticipants(mission));
    assertEquals(4, mapper.resolveRegisteredParticipants(mission));
  }

  @Test
  void shouldHandleNullMissionGracefully() {
    assertEquals(0, mapper.resolveCheckedInParticipants(null));
    assertEquals(0, mapper.resolveRegisteredParticipants(null));
  }

  private MissionParticipant buildParticipant(Instant startTime) {
    MissionParticipant p = new MissionParticipant();
    p.setStartTime(startTime);
    return p;
  }
}
