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
