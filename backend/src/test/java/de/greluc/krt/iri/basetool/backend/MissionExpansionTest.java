package de.greluc.krt.iri.basetool.backend;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.*;
import de.greluc.krt.iri.basetool.backend.repository.*;
import de.greluc.krt.iri.basetool.backend.service.MissionService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MissionExpansionTest {

  @MockitoBean private JwtDecoder jwtDecoder;

  @Autowired private MissionService missionService;

  @Autowired private MissionRepository missionRepository;

  @Autowired private ShipRepository shipRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private JobTypeRepository jobTypeRepository;

  @Autowired private ShipTypeRepository shipTypeRepository;

  @Test
  void testMissionExpansion() {
    // 1. Create User
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername("pilot1");
    user.setEmail("pilot@test.com");
    final User savedUser = userRepository.save(user);

    // 1b. Create ShipType
    ShipType fighter = new ShipType();
    fighter.setName("Fighter");
    fighter = shipTypeRepository.save(fighter);

    // 2. Create Ship
    Ship ship = new Ship();
    ship.setName("Test Ship");
    ship.setShipType(fighter);
    ship.setOwner(savedUser);
    ship = shipRepository.save(ship);

    // 3. Create Mission — uses the new CreateMissionRequest record (audit finding C-3 migration:
    // the legacy createMission(Mission) signature is gone, no caller can smuggle id/version/
    // owningSquadron through the create path anymore).
    Mission mission =
        missionService.createMission(
            new de.greluc.krt.iri.basetool.backend.model.dto.request.CreateMissionRequest(
                "Test Mission", null, null, "PLANNED", null, null, null, false, null, null));

    // 4. Add Participant first — a unit's ship must belong to a registered participant, so the
    // ship owner has to be signed up before the ship can be assigned to the unit.
    mission = missionService.addParticipant(mission.getId(), savedUser.getId());
    MissionParticipant participant =
        mission.getParticipants().stream()
            .filter(p -> p.getUser().getId().equals(savedUser.getId()))
            .findFirst()
            .orElseThrow();

    // 4b. Add Ship to Mission
    mission =
        missionService.addUnitToMission(
            mission.getId(), "Expansion Unit", fighter.getId(), ship.getId(), false, null);

    assertNotNull(mission.getAssignedUnits());
    assertEquals(1, mission.getAssignedUnits().size());
    MissionUnit missionShip = mission.getAssignedUnits().iterator().next();
    assertEquals(ship.getId(), missionShip.getShip().getId());

    // 5. Add Crew to Ship
    JobType pilot = new JobType();
    pilot.setName("Pilot");
    pilot.setArchetype(JobTypeArchetype.CREW);
    pilot = jobTypeRepository.save(pilot);

    JobType engineer = new JobType();
    engineer.setName("Engineer");
    engineer.setArchetype(JobTypeArchetype.CREW);
    engineer = jobTypeRepository.save(engineer);

    Set<UUID> jobTypeIds = Set.of(pilot.getId(), engineer.getId());
    mission =
        missionService.addCrewToShip(
            mission.getId(), missionShip.getId(), participant.getId(), jobTypeIds);

    // Verify
    Mission updatedMission = missionRepository.findById(mission.getId()).orElseThrow();
    assertEquals(1, updatedMission.getAssignedUnits().size());
    MissionUnit updatedMissionShip = updatedMission.getAssignedUnits().iterator().next();
    assertEquals(1, updatedMissionShip.getCrew().size());

    MissionCrew crew = updatedMissionShip.getCrew().iterator().next();
    assertEquals(savedUser.getId(), crew.getParticipant().getUser().getId());
    assertEquals(2, crew.getJobTypes().size());
    assertTrue(crew.getJobTypes().stream().anyMatch(jt -> jt.getName().equals("Pilot")));
  }
}
