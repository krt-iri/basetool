/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
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
class MissionServiceUnassignedParticipantsTest {

  @Mock private MissionRepository missionRepository;
  @Mock private UserRepository userRepository;
  @Mock private MissionParticipantRepository missionParticipantRepository;
  @Mock private MissionCrewRepository missionCrewRepository;
  @Mock private ShipRepository shipRepository;
  @Mock private JobTypeRepository jobTypeRepository;
  @Mock private SquadronRepository squadronRepository;

  @InjectMocks private MissionService missionService;

  @Test
  void getUnassignedParticipants_shouldReturnAllWhenNoneAssigned() {
    // Given
    UUID missionId = UUID.randomUUID();
    Mission mission = new Mission();
    mission.setId(missionId);

    MissionParticipant p1 = new MissionParticipant();
    p1.setId(UUID.randomUUID());
    MissionParticipant p2 = new MissionParticipant();
    p2.setId(UUID.randomUUID());
    mission.getParticipants().add(p1);
    mission.getParticipants().add(p2);

    when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

    // When
    List<MissionParticipant> result = missionService.getUnassignedParticipants(missionId);

    // Then
    assertEquals(2, result.size());
    assertTrue(result.contains(p1));
    assertTrue(result.contains(p2));
  }

  @Test
  void getUnassignedParticipants_shouldExcludeAssignedParticipants() {
    // Given
    UUID missionId = UUID.randomUUID();
    Mission mission = new Mission();
    mission.setId(missionId);

    MissionParticipant assigned = new MissionParticipant();
    assigned.setId(UUID.randomUUID());
    MissionParticipant unassigned = new MissionParticipant();
    unassigned.setId(UUID.randomUUID());
    mission.getParticipants().add(assigned);
    mission.getParticipants().add(unassigned);

    MissionUnit unit = new MissionUnit();
    unit.setId(UUID.randomUUID());
    MissionCrew crew = new MissionCrew();
    crew.setId(UUID.randomUUID());
    crew.setParticipant(assigned);
    unit.getCrew().add(crew);
    mission.getAssignedUnits().add(unit);

    when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

    // When
    List<MissionParticipant> result = missionService.getUnassignedParticipants(missionId);

    // Then
    assertEquals(1, result.size());
    assertTrue(result.contains(unassigned));
    assertFalse(result.contains(assigned));
  }

  @Test
  void getUnassignedParticipants_shouldReturnEmptyWhenAllAssigned() {
    // Given
    UUID missionId = UUID.randomUUID();
    Mission mission = new Mission();
    mission.setId(missionId);

    MissionParticipant p = new MissionParticipant();
    p.setId(UUID.randomUUID());
    mission.getParticipants().add(p);

    MissionUnit unit = new MissionUnit();
    unit.setId(UUID.randomUUID());
    MissionCrew crew = new MissionCrew();
    crew.setId(UUID.randomUUID());
    crew.setParticipant(p);
    unit.getCrew().add(crew);
    mission.getAssignedUnits().add(unit);

    when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

    // When
    List<MissionParticipant> result = missionService.getUnassignedParticipants(missionId);

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void getUnassignedParticipants_shouldThrowWhenMissionNotFound() {
    // Given
    UUID missionId = UUID.randomUUID();
    when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

    // When / Then
    assertThrows(RuntimeException.class, () -> missionService.getUnassignedParticipants(missionId));
  }
}
