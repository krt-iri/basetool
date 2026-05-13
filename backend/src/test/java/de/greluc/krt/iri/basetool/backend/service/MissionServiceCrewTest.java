package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.model.*;
import de.greluc.krt.iri.basetool.backend.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionServiceCrewTest {

  @Mock private MissionRepository missionRepository;
  @Mock private UserRepository userRepository;
  @Mock private MissionParticipantRepository missionParticipantRepository;
  @Mock private MissionCrewRepository missionCrewRepository;
  @Mock private ShipRepository shipRepository;
  @Mock private JobTypeRepository jobTypeRepository;
  @Mock private SquadronRepository squadronRepository;

  @InjectMocks private MissionService missionService;

  @Test
  void updateCrewInShip_shouldUpdateJobTypes() {
    UUID missionId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    UUID crewId = UUID.randomUUID();
    UUID jobTypeId = UUID.randomUUID();

    Mission mission = new Mission();
    mission.setId(missionId);

    MissionUnit unit = new MissionUnit();
    unit.setId(unitId);
    mission.getAssignedUnits().add(unit);

    MissionCrew crew = new MissionCrew();
    crew.setId(crewId);
    unit.getCrew().add(crew);

    JobType jobType = new JobType();
    jobType.setId(jobTypeId);
    jobType.setArchetype(JobTypeArchetype.CREW);

    when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
    when(jobTypeRepository.findById(jobTypeId)).thenReturn(Optional.of(jobType));

    Mission updatedMission =
        missionService.updateCrewInShip(missionId, unitId, crewId, Set.of(jobTypeId));

    assertNotNull(updatedMission);
    MissionUnit updatedUnit = updatedMission.getAssignedUnits().iterator().next();
    MissionCrew updatedCrew = updatedUnit.getCrew().iterator().next();
    assertEquals(1, updatedCrew.getJobTypes().size());
    assertEquals(jobTypeId, updatedCrew.getJobTypes().iterator().next().getId());

    // Option A: parent Mission.version must NOT be bumped by a sub-section (crew) write.
    verify(missionRepository, never()).save(any(Mission.class));
    verify(missionCrewRepository).save(crew);
  }

  @Test
  void updateCrewInShip_shouldThrowIfMissionNotFound() {
    UUID missionId = UUID.randomUUID();
    when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

    assertThrows(
        RuntimeException.class,
        () ->
            missionService.updateCrewInShip(
                missionId, UUID.randomUUID(), UUID.randomUUID(), Collections.emptySet()));
  }

  @Test
  void updateCrewInShip_shouldThrowIfUnitNotFound() {
    UUID missionId = UUID.randomUUID();
    Mission mission = new Mission();
    mission.setId(missionId);

    when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

    assertThrows(
        RuntimeException.class,
        () ->
            missionService.updateCrewInShip(
                missionId, UUID.randomUUID(), UUID.randomUUID(), Collections.emptySet()));
  }

  @Test
  void updateCrewInShip_shouldThrowIfCrewNotFound() {
    UUID missionId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();

    Mission mission = new Mission();
    mission.setId(missionId);

    MissionUnit unit = new MissionUnit();
    unit.setId(unitId);
    mission.getAssignedUnits().add(unit);

    when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

    assertThrows(
        RuntimeException.class,
        () ->
            missionService.updateCrewInShip(
                missionId, unitId, UUID.randomUUID(), Collections.emptySet()));
  }

  @Test
  void removeCrewFromShip_shouldRemoveCrew() {
    UUID missionId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    UUID crewId = UUID.randomUUID();

    Mission mission = new Mission();
    mission.setId(missionId);

    MissionUnit unit = new MissionUnit();
    unit.setId(unitId);
    mission.getAssignedUnits().add(unit);

    MissionCrew crew = new MissionCrew();
    crew.setId(crewId);
    unit.getCrew().add(crew);

    when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

    Mission updatedMission = missionService.removeCrewFromShip(missionId, unitId, crewId);

    assertNotNull(updatedMission);
    MissionUnit updatedUnit = updatedMission.getAssignedUnits().iterator().next();
    assertTrue(updatedUnit.getCrew().isEmpty());

    // Option A: parent Mission.version must NOT be bumped by a sub-section (crew) write.
    verify(missionRepository, never()).save(any(Mission.class));
  }

  @Test
  void removeCrewFromShip_shouldThrowIfCrewNotFound() {
    UUID missionId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();

    Mission mission = new Mission();
    mission.setId(missionId);

    MissionUnit unit = new MissionUnit();
    unit.setId(unitId);
    mission.getAssignedUnits().add(unit);

    when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

    assertThrows(
        RuntimeException.class,
        () -> missionService.removeCrewFromShip(missionId, unitId, UUID.randomUUID()));
  }
}
