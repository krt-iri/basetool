package de.greluc.krt.iri.basetool.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.service.MissionService;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class MissionControllerSecurityTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @MockitoBean private MissionService missionService;

  @MockitoBean
  private de.greluc.krt.iri.basetool.backend.service.MissionSecurityService missionSecurityService;

  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void updateCrew_WithOfficerRole_ShouldReturn200() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    UUID crewId = UUID.randomUUID();

    when(missionSecurityService.canManageMission(any(UUID.class), ArgumentMatchers.any()))
        .thenReturn(true);
    when(missionService.updateCrewInShip(any(), any(), any(), any())).thenReturn(new Mission());

    mockMvc
        .perform(
            put("/api/v1/missions/{id}/units/{unitId}/crew/{crewId}", missionId, unitId, crewId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobTypeIds\": [\"" + UUID.randomUUID() + "\"]}")
                .with(
                    jwt()
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_OFFICER"),
                            new SimpleGrantedAuthority("USER_MANAGE"),
                            new SimpleGrantedAuthority("MISSION_MANAGE"),
                            new SimpleGrantedAuthority("HANGAR_MANAGE"),
                            new SimpleGrantedAuthority("REFINERY_MANAGE"))))
        .andExpect(status().isOk());
  }

  @Test
  void updateCrew_WithSquadronMemberRole_ShouldReturn403() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    UUID crewId = UUID.randomUUID();

    mockMvc
        .perform(
            put("/api/v1/missions/{id}/units/{unitId}/crew/{crewId}", missionId, unitId, crewId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobTypeIds\": [\"" + UUID.randomUUID() + "\"]}")
                .with(
                    jwt()
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER"),
                            new SimpleGrantedAuthority("HANGAR_READ"),
                            new SimpleGrantedAuthority("HANGAR_WRITE"),
                            new SimpleGrantedAuthority("MISSION_READ"),
                            new SimpleGrantedAuthority("REFINERY_READ"),
                            new SimpleGrantedAuthority("REFINERY_WRITE"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateCrew_WithoutRole_ShouldReturn403() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    UUID crewId = UUID.randomUUID();

    mockMvc
        .perform(
            put("/api/v1/missions/{id}/units/{unitId}/crew/{crewId}", missionId, unitId, crewId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobTypeIds\": [\"" + UUID.randomUUID() + "\"]}")
                .with(jwt().authorities(Collections.emptyList())))
        .andExpect(status().isForbidden());
  }

  @Test
  void addManager_WithMissionManagerRole_ShouldReturn200() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    when(missionSecurityService.canManageManagers(any(UUID.class), any())).thenReturn(true);
    when(missionService.addManager(any(), any())).thenReturn(new Mission());

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/missions/{id}/managers/{userId}", missionId, userId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MISSION_MANAGER"))))
        .andExpect(status().isOk());
  }

  @Test
  void addManager_WithoutPermission_ShouldReturn403() throws Exception {
    UUID missionId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    when(missionSecurityService.canManageManagers(any(UUID.class), any())).thenReturn(false);

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/missions/{id}/managers/{userId}", missionId, userId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER"))))
        .andExpect(status().isForbidden());
  }
}
