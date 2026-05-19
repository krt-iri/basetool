package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronDto;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SquadronServiceTest {

  @Autowired private SquadronService squadronService;

  @Autowired private SquadronRepository squadronRepository;

  @Autowired private MissionRepository missionRepository;

  @Autowired private MissionParticipantRepository missionParticipantRepository;

  @Test
  void createSquadron_ShouldSaveShorthand() {
    Squadron s = new Squadron();
    s.setName("Test Squadron");
    s.setShorthand("TST");
    s.setDescription("Description");

    Squadron saved = squadronService.createSquadron(s);

    assertNotNull(saved.getId());
    assertEquals("TST", saved.getShorthand());
  }

  @Test
  void createSquadron_MissingShorthand_ShouldThrow() {
    Squadron s = new Squadron();
    s.setName("Incomplete");
    // No shorthand

    assertThrows(
        DataIntegrityViolationException.class,
        () -> {
          squadronService.createSquadron(s);
          squadronRepository.flush(); // Force DB write
        });
  }

  @Test
  void updateSquadron_ShouldUpdateShorthand() {
    Squadron s = new Squadron();
    s.setName("Update Test");
    s.setShorthand("UPD");
    s = squadronService.createSquadron(s);

    SquadronDto update =
        new SquadronDto(
            s.getId(), "Updated Name", "NEW", "Updated Desc", true, true, s.getVersion());

    Squadron updated = squadronService.updateSquadron(s.getId(), update);

    assertEquals("NEW", updated.getShorthand());
    assertEquals("Updated Name", updated.getName());
  }

  @Test
  void deleteSquadron_ShouldSetInactive() {
    // Given
    Squadron squadron = new Squadron();
    squadron.setName("Active Squadron");
    squadron.setShorthand("ACT");
    squadron = squadronService.createSquadron(squadron);

    Mission mission = new Mission();
    mission.setName("Test Mission");
    mission.setStatus("PLANNED");
    mission.setPlannedStartTime(Instant.now());
    // V87 made owning_squadron_id NOT NULL — stamp the test mission with the squadron
    // created above so persist does not violate the new constraint.
    mission.setOwningSquadron(squadron);
    mission = missionRepository.save(mission);

    MissionParticipant participant = new MissionParticipant();
    participant.setMission(mission);
    participant.setGuestName("Test Guest");
    participant.setSquadron(squadron);
    missionParticipantRepository.save(participant);

    // When
    UUID squadronId = squadron.getId();
    squadronService.deleteSquadron(squadronId);

    // Then
    Squadron updatedSquadron = squadronRepository.findById(squadronId).orElseThrow();
    assertFalse(updatedSquadron.isActive());
  }
}
