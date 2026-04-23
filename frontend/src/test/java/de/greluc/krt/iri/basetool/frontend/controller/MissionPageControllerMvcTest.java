package de.greluc.krt.iri.basetool.frontend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@SuppressWarnings("unchecked")
class MissionPageControllerMvcTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private BackendApiClient backendApiClient;
    
    @MockitoBean
    private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void createMission_WithEmptyDescription_ShouldSucceed() throws Exception {
        // Prepare Mocks
        when(backendApiClient.post(any(String.class), any(), Mockito.eq(Void.class))).thenReturn(null);

        // Perform Request
        mockMvc.perform(post("/missions")
                .param("name", "Test Mission")
                .param("description", "") // Empty description triggers StringTrimmerEditor -> null
                .param("status", "PLANNED")
                .param("plannedStartTime", "2026-02-10T10:00")
                .param("plannedEndTime", "2026-02-10T12:00")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(redirectedUrl("/missions"));
    }
    @Test
    @WithMockUser(roles = "OFFICER")
    void missionDetail_ShouldRenderWithoutErrors() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        
        Map<String, Object> shipTypeData = new java.util.HashMap<>();
        shipTypeData.put("id", UUID.randomUUID().toString());
        shipTypeData.put("name", "Fighter");

        Map<String, Object> unitData = new java.util.HashMap<>();
        unitData.put("id", unitId.toString());
        unitData.put("name", "Alpha Unit");
        unitData.put("highValueUnit", false);
        unitData.put("shipType", shipTypeData);
        unitData.put("ship", null);
        unitData.put("frequency", 123.45);
        unitData.put("crew", new java.util.ArrayList<>());

        Map<String, Object> userData = new java.util.HashMap<>();
        userData.put("username", "TestUser");
        userData.put("displayName", "Test User");

        Map<String, Object> locationData = new java.util.HashMap<>();
        locationData.put("name", "Test Location");

        Map<String, Object> orderData = new java.util.HashMap<>();
        orderData.put("id", UUID.randomUUID().toString());
        orderData.put("startedAt", "2026-03-29T18:00:00Z");
        orderData.put("status", "OPEN");
        orderData.put("location", locationData);
        orderData.put("owner", userData);
        orderData.put("durationMinutes", 60);
        orderData.put("goods", new java.util.ArrayList<>());

        Map<String, Object> itemData = new java.util.HashMap<>();
        itemData.put("id", UUID.randomUUID().toString());
        itemData.put("user", userData);
        itemData.put("location", locationData);
        
        Map<String, Object> materialData = new java.util.HashMap<>();
        materialData.put("name", "Quantanium");
        itemData.put("material", materialData);
        
        itemData.put("amount", 100);
        itemData.put("quality", 50);
        itemData.put("jobOrderDisplayId", 123);

        UUID managerId = UUID.randomUUID();
        de.greluc.krt.iri.basetool.frontend.model.dto.UserReferenceDto manager = 
            new de.greluc.krt.iri.basetool.frontend.model.dto.UserReferenceDto(managerId, "manager", null, "Test Manager", 0);

        MissionDto mission = new MissionDto(
                missionId, "Test Mission", null, null, "PLANNED", null, null, null, null, null, false,
                Collections.emptySet(), Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                Collections.emptyList(), Collections.emptyList(), null, null, java.util.Set.of(manager), true, true, 1L, 0, 0
        );

        when(backendApiClient.get(eq("/api/v1/missions/" + missionId), any(ParameterizedTypeReference.class), eq(true)))
            .thenReturn(mission);
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class), eq(true)))
            .thenReturn(Collections.emptyList());

        // This will fail with TemplateProcessingException if the th:onclick syntax is invalid
        mockMvc.perform(get("/missions/" + missionId))
                .andExpect(status().isOk())
                .andExpect(view().name("mission-detail"));
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void missionDetail_AsAuthenticated_ShouldShowParticipationColumn() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        
        de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto participant = 
            new de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto(
                participantId, null, "P1", null, null, null, null, null, null, 
                de.greluc.krt.iri.basetool.frontend.model.PayoutPreference.PAYOUT, 1L
            );

        MissionDto mission = new MissionDto(
                missionId, "Auth Mission", null, null, "PLANNED", null, null, null, null, null, false,
                java.util.Set.of(participant), Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                Collections.emptyList(), Collections.emptyList(), null, null, Collections.emptySet(), true, true, 1L, 1, 1
        );
        when(backendApiClient.get(eq("/api/v1/missions/" + missionId), any(ParameterizedTypeReference.class), eq(true)))
            .thenReturn(mission);
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class), eq(true)))
            .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/missions/" + missionId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Teilnahme (%)")));
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void missionDetail_ShouldRenderParticipantsCounter_AsXSlashY() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID p1Id = UUID.randomUUID();
        UUID p2Id = UUID.randomUUID();
        UUID p3Id = UUID.randomUUID();

        de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto p1 =
            new de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto(
                p1Id, null, "P1", null, null, null, null, null, null,
                de.greluc.krt.iri.basetool.frontend.model.PayoutPreference.PAYOUT, 1L);
        de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto p2 =
            new de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto(
                p2Id, null, "P2", null, null, null, null, null, null,
                de.greluc.krt.iri.basetool.frontend.model.PayoutPreference.PAYOUT, 1L);
        de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto p3 =
            new de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto(
                p3Id, null, "P3", null, null, null, null, null, null,
                de.greluc.krt.iri.basetool.frontend.model.PayoutPreference.PAYOUT, 1L);

        // 2 checked-in out of 3 registered
        MissionDto mission = new MissionDto(
                missionId, "Counter Mission", null, null, "PLANNED", null, null, null, null, null, false,
                java.util.Set.of(p1, p2, p3), Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                Collections.emptyList(), Collections.emptyList(), null, null, Collections.emptySet(), true, true, 1L, 2, 3
        );
        when(backendApiClient.get(eq("/api/v1/missions/" + missionId), any(ParameterizedTypeReference.class), eq(true)))
            .thenReturn(mission);
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class), eq(true)))
            .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/missions/" + missionId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2/3")));
    }

    @Test
    void missionDetail_AsGuest_ShouldHideParticipationColumn() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        
        de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto participant = 
            new de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto(
                participantId, null, "P1", null, null, null, null, null, null, 
                de.greluc.krt.iri.basetool.frontend.model.PayoutPreference.PAYOUT, 1L
            );

        MissionDto mission = new MissionDto(
                missionId, "Guest Mission", null, null, "PLANNED", null, null, null, null, null, false,
                java.util.Set.of(participant), Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                Collections.emptyList(), Collections.emptyList(), null, null, Collections.emptySet(), true, true, 1L, 0, 1
        );
        when(backendApiClient.get(eq("/api/v1/missions/" + missionId), any(ParameterizedTypeReference.class), eq(true)))
            .thenReturn(mission);
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class), eq(true)))
            .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/missions/" + missionId))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Teilnahme (%)"))));
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void setMissionOwner_WithValidIds_ShouldReturn200() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(backendApiClient.put(eq("/api/v1/missions/" + missionId + "/owner/" + userId), eq(null), eq(Void.class), eq(false)))
                .thenReturn(null);

        mockMvc.perform(put("/missions/" + missionId + "/owner/" + userId)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void setMissionOwner_WithInvalidMissionId_ShouldReturn400() throws Exception {
        mockMvc.perform(put("/missions/not-a-uuid/owner/" + UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void addManager_WithInvalidIds_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/missions/not-a-uuid/managers/not-a-uuid")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void updateActualTime_Success_ShouldReturn200WithRefreshedMission() throws Exception {
        UUID missionId = UUID.randomUUID();
        MissionDto current = new MissionDto(
                missionId, "M", null, null, "PLANNED", null, null, null, null, null, false,
                Collections.emptySet(), Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                Collections.emptyList(), Collections.emptyList(), null, null, Collections.emptySet(), true, true, 1L, 0, 0
        );
        MissionDto refreshed = new MissionDto(
                missionId, "M", null, null, "PLANNED", null, null, java.time.Instant.parse("2026-04-20T12:00:00Z"),
                null, null, false,
                Collections.emptySet(), Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                Collections.emptyList(), Collections.emptyList(), null, null, Collections.emptySet(), true, true, 2L, 0, 0
        );
        when(backendApiClient.get(eq("/api/v1/missions/" + missionId), eq(MissionDto.class)))
                .thenReturn(current).thenReturn(refreshed);
        when(backendApiClient.put(eq("/api/v1/missions/" + missionId), any(MissionDto.class), eq(Void.class)))
                .thenReturn(null);

        String body = "{\"field\":\"actualStartTime\",\"value\":\"2026-04-20T12:00:00Z\",\"version\":1}";
        mockMvc.perform(post("/missions/" + missionId + "/actual-time")
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void updateActualTime_OptimisticLockConflict_ShouldReturn409() throws Exception {
        UUID missionId = UUID.randomUUID();
        MissionDto current = new MissionDto(
                missionId, "M", null, null, "PLANNED", null, null, null, null, null, false,
                Collections.emptySet(), Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                Collections.emptyList(), Collections.emptyList(), null, null, Collections.emptySet(), true, true, 5L, 0, 0
        );
        when(backendApiClient.get(eq("/api/v1/missions/" + missionId), eq(MissionDto.class)))
                .thenReturn(current);
        when(backendApiClient.put(eq("/api/v1/missions/" + missionId), any(MissionDto.class), eq(Void.class)))
                .thenThrow(new de.greluc.krt.iri.basetool.frontend.service.BackendServiceException("Conflict", null, 409));

        String body = "{\"field\":\"actualEndTime\",\"value\":\"2026-04-20T13:00:00Z\",\"version\":1}";
        mockMvc.perform(post("/missions/" + missionId + "/actual-time")
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void updateActualTime_InvalidField_ShouldReturn400() throws Exception {
        UUID missionId = UUID.randomUUID();
        String body = "{\"field\":\"unknownField\",\"value\":\"2026-04-20T13:00:00Z\",\"version\":1}";
        mockMvc.perform(post("/missions/" + missionId + "/actual-time")
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void addManager_WithBackendError_ShouldPropagateStatus() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(backendApiClient.post(eq("/api/v1/missions/" + missionId + "/managers/" + userId), eq(null), eq(String.class), eq(false)))
                .thenThrow(new de.greluc.krt.iri.basetool.frontend.service.BackendServiceException("Error", null, 400));

        mockMvc.perform(post("/missions/" + missionId + "/managers/" + userId)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
