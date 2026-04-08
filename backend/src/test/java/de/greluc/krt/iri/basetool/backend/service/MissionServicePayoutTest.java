package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.PayoutPreference;
import de.greluc.krt.iri.basetool.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionServicePayoutTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MissionParticipantRepository missionParticipantRepository;

    @InjectMocks
    private MissionService missionService;

    @Test
    void shouldCheckInParticipant() {
        // Given
        UUID missionId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        Mission mission = new Mission();
        mission.setId(missionId);
        mission.setActualStartTime(Instant.now().minusSeconds(3600));
        
        MissionParticipant p = new MissionParticipant();
        p.setId(participantId);
        p.setMission(mission);
        mission.getParticipants().add(p);

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(missionParticipantRepository.findById(participantId)).thenReturn(Optional.of(p));
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Mission updatedMission = missionService.checkIn(missionId, participantId);

        // Then
        MissionParticipant updatedParticipant = updatedMission.getParticipants().iterator().next();
        assertNotNull(updatedParticipant.getStartTime(), "Start time should be set");
        verify(missionRepository).save(mission);
    }

    @Test
    void shouldCheckOutParticipant() {
        // Given
        UUID missionId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        Mission mission = new Mission();
        mission.setId(missionId);
        
        MissionParticipant p = new MissionParticipant();
        p.setId(participantId);
        p.setMission(mission);
        p.setStartTime(Instant.now().minusSeconds(3600));
        mission.getParticipants().add(p);

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(missionParticipantRepository.findById(participantId)).thenReturn(Optional.of(p));
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Mission updatedMission = missionService.checkOut(missionId, participantId);

        // Then
        MissionParticipant updatedParticipant = updatedMission.getParticipants().iterator().next();
        assertNotNull(updatedParticipant.getEndTime(), "End time should be set");
        verify(missionRepository).save(mission);
    }

    @Test
    void shouldUpdatePayoutPreference() {
        // Given
        UUID missionId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        Mission mission = new Mission();
        mission.setId(missionId);
        
        MissionParticipant p = new MissionParticipant();
        p.setId(participantId);
        p.setMission(mission);
        p.setPayoutPreference(PayoutPreference.PAYOUT);
        mission.getParticipants().add(p);

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Mission updatedMission = missionService.updatePayoutPreference(missionId, participantId, PayoutPreference.DONATE);

        // Then
        MissionParticipant updatedParticipant = updatedMission.getParticipants().iterator().next();
        assertEquals(PayoutPreference.DONATE, updatedParticipant.getPayoutPreference());
        verify(missionRepository).save(mission);
    }
}
