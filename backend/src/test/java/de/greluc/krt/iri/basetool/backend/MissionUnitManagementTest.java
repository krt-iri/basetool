package de.greluc.krt.iri.basetool.backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.backend.model.*;
import de.greluc.krt.iri.basetool.backend.repository.*;
import de.greluc.krt.iri.basetool.backend.service.MissionService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MissionUnitManagementTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;
  @Autowired private MissionRepository missionRepository;
  @Autowired private ShipRepository shipRepository;
  @Autowired private ShipTypeRepository shipTypeRepository;
  @Autowired private MissionService missionService;

  @MockitoBean private JwtDecoder jwtDecoder;

  private User officerUser;
  private Mission mission;
  private Ship ship;
  private MissionUnit unit;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    officerUser = new User();
    officerUser.setId(UUID.randomUUID());
    officerUser.setUsername("officer_unit_mgmt");
    userRepository.save(officerUser);

    ShipType st = new ShipType();
    st.setName("Fighter");
    shipTypeRepository.save(st);

    ship = new Ship();
    ship.setName("Test Ship");
    ship.setOwner(officerUser);
    ship.setShipType(st);
    shipRepository.save(ship);

    mission =
        missionService.createMission(
            new de.greluc.krt.iri.basetool.backend.model.dto.request.CreateMissionRequest(
                "Test Mission Unit Mgmt", null, null, "PLANNED", null, null, null, false, null));

    mission =
        missionService.addUnitToMission(
            mission.getId(), "Initial Unit", st.getId(), ship.getId(), false, 123.45);
    unit = mission.getAssignedUnits().iterator().next();
  }

  @Test
  void testUpdateUnit_Officer_Allowed() throws Exception {
    String updateJson =
        String.format(
            "{\"name\": \"Updated Unit\", \"shipTypeId\": \"%s\", \"shipId\": \"%s\","
                + " \"highValueUnit\": true, \"frequency\": 111.11}",
            unit.getShipType().getId(), ship.getId());

    mockMvc
        .perform(
            put("/api/v1/missions/" + mission.getId() + "/units/" + unit.getId())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_OFFICER"),
                            new SimpleGrantedAuthority("USER_MANAGE"),
                            new SimpleGrantedAuthority("MISSION_MANAGE"),
                            new SimpleGrantedAuthority("HANGAR_MANAGE"),
                            new SimpleGrantedAuthority("REFINERY_MANAGE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
        .andExpect(status().isOk());

    Mission updatedMission = missionRepository.findById(mission.getId()).orElseThrow();
    MissionUnit updatedUnit = updatedMission.getAssignedUnits().iterator().next();

    org.junit.jupiter.api.Assertions.assertEquals("Updated Unit", updatedUnit.getName());
    org.junit.jupiter.api.Assertions.assertTrue(updatedUnit.isHighValueUnit());
    org.junit.jupiter.api.Assertions.assertEquals(111.11, updatedUnit.getFrequency());
  }

  @Test
  void testDeleteUnit_Officer_Allowed() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/missions/" + mission.getId() + "/units/" + unit.getId())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_OFFICER"),
                            new SimpleGrantedAuthority("USER_MANAGE"),
                            new SimpleGrantedAuthority("MISSION_MANAGE"),
                            new SimpleGrantedAuthority("HANGAR_MANAGE"),
                            new SimpleGrantedAuthority("REFINERY_MANAGE"))))
        .andExpect(status().isOk());

    Mission updatedMission = missionRepository.findById(mission.getId()).orElseThrow();
    assertFalse(
        updatedMission.getAssignedUnits().stream().anyMatch(u -> u.getId().equals(unit.getId())));
  }
}
