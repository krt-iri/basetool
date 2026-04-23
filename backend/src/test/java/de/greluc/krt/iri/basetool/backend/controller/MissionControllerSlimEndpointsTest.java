package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionCrew;
import de.greluc.krt.iri.basetool.backend.model.MissionFrequency;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.MissionUnit;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.service.MissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the additive slim sub-resource endpoints introduced for multi-user concurrency
 * on the mission detail page (Option A, Paket 2). The legacy MissionDto-returning endpoints
 * are deprecated via @ApiDeprecation(sunset = 2026-10-20) and remain functional; these tests
 * focus on the new {@code /slim} endpoints: they must be reachable under the same role gates,
 * they must return slim sub-DTOs (not the full MissionDto), and DELETE variants must return
 * 204 No Content.
 */
@SpringBootTest
class MissionControllerSlimEndpointsTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private MissionService missionService;

    @MockitoBean
    private de.greluc.krt.iri.basetool.backend.service.MissionSecurityService missionSecurityService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private SimpleGrantedAuthority officer() {
        return new SimpleGrantedAuthority("ROLE_OFFICER");
    }

    private Mission missionWithUnit(UUID unitId) {
        Mission mission = new Mission();
        MissionUnit unit = new MissionUnit();
        unit.setId(unitId);
        unit.setName("Alpha");
        unit.setCrew(new LinkedHashSet<>());
        Set<MissionUnit> units = new LinkedHashSet<>();
        units.add(unit);
        mission.setAssignedUnits(units);
        return mission;
    }

    @Test
    void addUnitSlim_returnsSlimListOfUnits() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        when(missionSecurityService.canManageMission(any(UUID.class), any())).thenReturn(true);
        when(missionService.addUnitToMission(any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(missionWithUnit(unitId));

        mockMvc.perform(post("/api/v1/missions/{id}/units/slim", missionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alpha\",\"isHighValueUnit\":false}")
                        .with(jwt().authorities(officer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(unitId.toString()))
                .andExpect(jsonPath("$[0].name").value("Alpha"));
    }

    @Test
    void updateUnitSlim_returnsSingleSlimUnit() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        when(missionSecurityService.canManageMission(any(UUID.class), any())).thenReturn(true);
        when(missionService.updateMissionUnit(any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(missionWithUnit(unitId));

        mockMvc.perform(put("/api/v1/missions/{id}/units/{unitId}/slim", missionId, unitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alpha\",\"isHighValueUnit\":false}")
                        .with(jwt().authorities(officer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(unitId.toString()))
                .andExpect(jsonPath("$.name").value("Alpha"));
    }

    @Test
    void deleteUnitSlim_returns204NoContent() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        when(missionSecurityService.canManageMission(any(UUID.class), any())).thenReturn(true);
        when(missionService.removeMissionUnit(any(), any())).thenReturn(new Mission());

        mockMvc.perform(delete("/api/v1/missions/{id}/units/{unitId}/slim", missionId, unitId)
                        .with(jwt().authorities(officer())))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void addManagerSlim_returnsSlimUserReferenceList() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Mission mission = new Mission();
        User manager = new User();
        manager.setId(userId);
        manager.setUsername("user.one");
        manager.setFirstName("User");
        manager.setLastName("One");
        Set<User> managers = new HashSet<>();
        managers.add(manager);
        mission.setManagers(managers);

        when(missionSecurityService.canManageManagers(any(UUID.class), any())).thenReturn(true);
        when(missionService.addManager(any(), any())).thenReturn(mission);

        mockMvc.perform(post("/api/v1/missions/{id}/managers/{userId}/slim", missionId, userId)
                        .with(jwt().authorities(officer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(userId.toString()))
                .andExpect(jsonPath("$[0].username").value("user.one"));
    }

    @Test
    void removeManagerSlim_returns204NoContent() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(missionSecurityService.canManageManagers(any(UUID.class), any())).thenReturn(true);
        when(missionService.removeManager(any(), any())).thenReturn(new Mission());

        mockMvc.perform(delete("/api/v1/missions/{id}/managers/{userId}/slim", missionId, userId)
                        .with(jwt().authorities(officer())))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void addFrequencySlim_returnsSlimFrequencyList() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID frequencyTypeId = UUID.randomUUID();
        UUID frequencyId = UUID.randomUUID();

        Mission mission = new Mission();
        MissionFrequency freq = new MissionFrequency();
        freq.setId(frequencyId);
        freq.setValue(new BigDecimal("27.555"));
        Set<MissionFrequency> freqs = new LinkedHashSet<>();
        freqs.add(freq);
        mission.setFrequencies(freqs);

        when(missionSecurityService.canManageMission(any(UUID.class), any())).thenReturn(true);
        when(missionService.addOrUpdateMissionFrequency(any(), any(), any())).thenReturn(mission);

        mockMvc.perform(post("/api/v1/missions/{id}/frequencies/slim", missionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frequencyTypeId\":\"" + frequencyTypeId + "\",\"value\":27.555}")
                        .with(jwt().authorities(officer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(frequencyId.toString()));
    }

    @Test
    void deleteUnitSlim_withoutPermission_returns403() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        when(missionSecurityService.canManageMission(any(UUID.class), any())).thenReturn(false);

        mockMvc.perform(delete("/api/v1/missions/{id}/units/{unitId}/slim", missionId, unitId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER"))))
                .andExpect(status().isForbidden());
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }
}
