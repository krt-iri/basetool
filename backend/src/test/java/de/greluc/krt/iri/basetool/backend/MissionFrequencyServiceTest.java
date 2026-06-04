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

package de.greluc.krt.iri.basetool.backend;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.*;
import de.greluc.krt.iri.basetool.backend.repository.*;
import de.greluc.krt.iri.basetool.backend.service.MissionService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MissionFrequencyServiceTest {

  @Autowired private MissionService missionService;

  @Autowired private MissionRepository missionRepository;

  @Autowired private FrequencyTypeRepository frequencyTypeRepository;

  @Test
  void testAddMissionFrequency() {
    Mission mission = new Mission();
    mission.setName("Test Frequency Mission");
    mission.setStatus("PLANNED");
    mission = missionRepository.save(mission);

    FrequencyType type = new FrequencyType();
    type.setName("VHF");
    type.setDescription("VHF Frequency");
    type = frequencyTypeRepository.save(type);

    Mission updatedMission =
        missionService.addOrUpdateMissionFrequency(
            mission.getId(), type.getId(), new BigDecimal("123.45"));

    assertNotNull(updatedMission);
    assertEquals(1, updatedMission.getFrequencies().size());
    MissionFrequency freq = updatedMission.getFrequencies().iterator().next();
    assertEquals("VHF", freq.getFrequencyType().getName());
    assertEquals(new BigDecimal("123.45"), freq.getValue());
  }
}
