package de.greluc.krt.iri.basetool.backend;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import de.greluc.krt.iri.basetool.backend.service.MissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SquadronRepository squadronRepository;

    @Mock
    private MissionParticipantRepository missionParticipantRepository;

    @InjectMocks
    private MissionService missionService;

    @Test
    void addParticipant_ShouldMatchGuestNameToExistingUserCaseInsensitive() {
        UUID missionId = UUID.randomUUID();
        Mission mission = new Mission();
        mission.setId(missionId);

        UUID userId = UUID.randomUUID();
        User existingUser = new User();
        existingUser.setId(userId);
        existingUser.setUsername("testuser");

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(userRepository.findByUsernameIgnoreCaseOrDisplayNameIgnoreCase("TestUser", "TestUser")).thenReturn(Optional.of(existingUser));
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(squadronRepository.findByShorthand("IRI")).thenReturn(Optional.of(new Squadron()));
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

        Mission result = missionService.addParticipant(missionId, null, "TestUser", null, "No comment");

        assertEquals(1, result.getParticipants().size());
        MissionParticipant participant = result.getParticipants().iterator().next();
        assertNotNull(participant.getUser(), "User should be mapped from guest name");
        assertEquals(userId, participant.getUser().getId());
        assertNull(participant.getGuestName(), "Guest name should be nullified since user was found");
    }

    @Test
    void searchMissions_ShouldCallRepository() {
        String query = "Test";
        Instant start = Instant.now();
        Instant end = Instant.now().plus(1, ChronoUnit.DAYS);
        List<String> status = List.of("PLANNED", "ACTIVE", "COMPLETED", "CANCELLED"); // Default expected when null passed
        
        Pageable pageable = PageRequest.of(0, 10);
        when(missionRepository.searchMissions(query, start, end, status, null, null, pageable)).thenReturn(Page.empty());

        Page<Mission> result = missionService.searchMissions(query, start, end, null, null, null, pageable);

        verify(missionRepository).searchMissions(query, start, end, status, null, null, pageable);
    }

    @Test
    void updateMission_ShouldSetActualStartTime_WhenStatusChangesToActive() {
        UUID id = UUID.randomUUID();
        Mission existing = new Mission();
        existing.setId(id);
        existing.setStatus("PLANNED");
        existing.setActualStartTime(null);

        Mission details = new Mission();
        details.setStatus("ACTIVE");

        when(missionRepository.findById(id)).thenReturn(Optional.of(existing));
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArguments()[0]);

        Mission updated = missionService.updateMission(id, details);

        assertNotNull(updated.getActualStartTime());
    }

    @Test
    void updateMission_ShouldUseProvidedActualStartTime() {
        UUID id = UUID.randomUUID();
        Mission existing = new Mission();
        existing.setId(id);
        existing.setStatus("PLANNED");

        Mission details = new Mission();
        details.setStatus("ACTIVE");
        Instant manualStart = Instant.now().minus(1, ChronoUnit.HOURS);
        details.setActualStartTime(manualStart);

        when(missionRepository.findById(id)).thenReturn(Optional.of(existing));
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArguments()[0]);

        Mission updated = missionService.updateMission(id, details);

        assertEquals(manualStart, updated.getActualStartTime());
    }

    @Test
    void updateMission_ShouldNotSetActualStartTime_WhenStatusIsActiveButAlreadyActive() {
        UUID id = UUID.randomUUID();
        Mission existing = new Mission();
        existing.setId(id);
        existing.setStatus("ACTIVE");
        existing.setActualStartTime(null);

        Mission details = new Mission();
        details.setStatus("ACTIVE");

        when(missionRepository.findById(id)).thenReturn(Optional.of(existing));
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArguments()[0]);

        Mission updated = missionService.updateMission(id, details);

        assertNull(updated.getActualStartTime());
    }

    @Test
    void createMission_ShouldThrowException_WhenMeetingTimeAfterPlannedStart() {
        Mission mission = new Mission();
        mission.setPlannedStartTime(Instant.now());
        mission.setMeetingTime(Instant.now().plus(1, ChronoUnit.HOURS));

        assertThrows(IllegalArgumentException.class, () -> missionService.createMission(mission));
    }

    @Test
    void createMission_ShouldThrowException_WhenPlannedStartAfterPlannedEnd() {
        Mission mission = new Mission();
        mission.setPlannedStartTime(Instant.now().plus(2, ChronoUnit.HOURS));
        mission.setPlannedEndTime(Instant.now().plus(1, ChronoUnit.HOURS));

        assertThrows(IllegalArgumentException.class, () -> missionService.createMission(mission));
    }
}
