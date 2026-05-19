package de.greluc.krt.iri.basetool.backend;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import de.greluc.krt.iri.basetool.backend.service.MissionService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MissionParticipantSquadronTest {

  @Autowired private MissionService missionService;

  @Autowired private MissionRepository missionRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private SquadronRepository squadronRepository;

  private Squadron iridium;

  private Mission mission;
  private Squadron testSquadron;

  @BeforeEach
  void setup() {
    iridium = squadronRepository.findById(Squadron.IRIDIUM_ID).orElseThrow();
    mission = new Mission();
    mission.setOwningSquadron(iridium);
    mission.setName("Test Mission");
    mission.setStatus("PLANNED");
    mission = missionRepository.save(mission);

    testSquadron = new Squadron();
    testSquadron.setName("Test Squadron");
    testSquadron.setShorthand("TSQ");
    testSquadron = squadronRepository.save(testSquadron);

    // Ensure IRI exists (DataInitializer should have created it, but let's be safe/check)
    if (squadronRepository.findByShorthand("IRI").isEmpty()) {
      Squadron iri = new Squadron();
      iri.setName("IRIDIUM");
      iri.setShorthand("IRI");
      squadronRepository.save(iri);
    }
  }

  @Test
  void addParticipant_User_ShouldAssignIRI() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername("testuser");
    final User savedUser = userRepository.save(user);

    // Act
    Mission updatedMission = missionService.addParticipant(mission.getId(), savedUser.getId());

    // Assert
    MissionParticipant participant =
        updatedMission.getParticipants().stream()
            .filter(p -> p.getUser().getId().equals(savedUser.getId()))
            .findFirst()
            .orElseThrow();

    assertNotNull(participant.getSquadron());
    assertEquals("IRI", participant.getSquadron().getShorthand());
  }

  @Test
  void addParticipant_Guest_WithSquadronId_ShouldAssignSquadron() {
    // Act
    Mission updatedMission =
        missionService.addParticipant(
            mission.getId(), null, "Guest", null, "Comment", testSquadron.getId());

    // Assert
    MissionParticipant participant =
        updatedMission.getParticipants().stream()
            .filter(p -> "Guest".equals(p.getGuestName()))
            .findFirst()
            .orElseThrow();

    assertNotNull(participant.getSquadron());
    assertEquals("TSQ", participant.getSquadron().getShorthand());
  }

  @Test
  void addParticipant_Guest_WithInvalidSquadronId_ShouldAssignNull() {
    // Act
    Mission updatedMission =
        missionService.addParticipant(
            mission.getId(), null, "Guest2", null, "Comment", UUID.randomUUID());

    // Assert
    MissionParticipant participant =
        updatedMission.getParticipants().stream()
            .filter(p -> "Guest2".equals(p.getGuestName()))
            .findFirst()
            .orElseThrow();

    assertNull(participant.getSquadron());
  }
}
