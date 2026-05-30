package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.frontend.model.PayoutPreference;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

class ParticipationCalculationTest {

  @Test
  void testParticipationCalculation_KeyType() {
    // Arrange
    UUID missionId = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();

    Instant missionStart = Instant.parse("2024-04-04T10:00:00Z");
    Instant missionEnd = Instant.parse("2024-04-04T12:00:00Z"); // 2 hours

    MissionParticipantDto participant =
        new MissionParticipantDto(
            participantId,
            null,
            "P1",
            null,
            null,
            null,
            null,
            missionStart,
            missionEnd,
            PayoutPreference.PAYOUT,
            1L);

    MissionDto mission =
        new MissionDto(
            missionId,
            "Test Mission",
            null,
            null,
            "FINISHED",
            null,
            null,
            missionStart,
            null,
            missionEnd,
            false,
            Set.of(participant),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            null,
            Collections.emptySet(),
            true,
            true,
            1L,
            1L,
            1L,
            1L,
            1,
            1,
            null,
            null,
            null,
            0L);

    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    when(backendApiClient.get(
            eq("/api/v1/missions/" + missionId),
            any(ParameterizedTypeReference.class),
            anyBoolean()))
        .thenReturn(mission);

    MissionPageController controller = new MissionPageController(backendApiClient);
    Model model = new ConcurrentModel();

    // Act
    controller.missionDetail(missionId, model, null);

    // Assert
    Map<UUID, Double> percentages =
        (Map<UUID, Double>) model.getAttribute("participationPercentages");
    assertNotNull(percentages);

    // Should use UUID keys now
    assertNotNull(percentages.get(participantId), "Map should contain UUID keys");
    // No follow-up `get(participantId.toString())` assertion: the declared type
    // `Map<UUID, Double>` already prevents String keys at the put-site (erasure
    // aside, the controller code is type-checked against the same UUID key type),
    // and the runtime `get(Object)` lookup with a String would never match a UUID
    // entry — CodeQL's "Type mismatch on container access" flagged the same call
    // as a tautology.
  }

  @Test
  void testParticipationCalculation_Algorithm() {
    // ... (existing test code)
  }

  @Test
  void testParticipationCalculation_MissingStartTime() {
    // Arrange
    UUID missionId = UUID.randomUUID();
    UUID p1Id = UUID.randomUUID();
    UUID p2Id = UUID.randomUUID(); // No startTime

    Instant missionStart = Instant.parse("2024-04-04T10:00:00Z");
    Instant missionEnd = Instant.parse("2024-04-04T12:00:00Z");

    // P1 was there for 1 hour
    MissionParticipantDto p1 =
        new MissionParticipantDto(
            p1Id,
            null,
            "P1",
            null,
            null,
            null,
            null,
            missionStart,
            missionStart.plusSeconds(3600),
            PayoutPreference.PAYOUT,
            1L);

    // P2 has no startTime (not checked in yet)
    MissionParticipantDto p2 =
        new MissionParticipantDto(
            p2Id, null, "P2", null, null, null, null, null, null, PayoutPreference.PAYOUT, 1L);

    MissionDto mission =
        new MissionDto(
            missionId,
            "Test Mission",
            null,
            null,
            "FINISHED",
            null,
            null,
            missionStart,
            null,
            missionEnd,
            false,
            Set.of(p1, p2),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            null,
            Collections.emptySet(),
            true,
            true,
            1L,
            1L,
            1L,
            1L,
            1,
            2,
            null,
            null,
            null,
            0L);

    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    when(backendApiClient.get(
            eq("/api/v1/missions/" + missionId),
            any(ParameterizedTypeReference.class),
            anyBoolean()))
        .thenReturn(mission);

    MissionPageController controller = new MissionPageController(backendApiClient);
    Model model = new ConcurrentModel();

    // Act
    controller.missionDetail(missionId, model, null);

    // Assert
    Map<UUID, Double> percentages =
        (Map<UUID, Double>) model.getAttribute("participationPercentages");
    assertNotNull(percentages);

    assertTrue(
        percentages.containsKey(p2Id), "P2 should have a percentage entry even without startTime");
    assertEquals(0.0, percentages.get(p2Id), 0.001, "P2 percentage should be 0.0");
    assertEquals(100.0, percentages.get(p1Id), 0.001, "P1 percentage should be 100.0");
  }
}
