package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.PayoutPreference;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MissionParticipantDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class MissionSecurityRenderingTest {

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
    void missionDetail_AsAnonymous_ShouldDisableRegisteredParticipantPayoutDropdown() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        UserDto user = new UserDto(userId, "TestUser", "Test User", "Test User", null, null, null, null, null, null, null, null, false, false, true, 1L, null);
        MissionParticipantDto participant = new MissionParticipantDto(participantId, user, null, null, null, null, null, null, null, PayoutPreference.PAYOUT, 1L);

        MissionDto mission = new MissionDto(
                missionId, "Test Mission", null, null, "PLANNED", null, null, null, null, null, false,
                Set.of(participant), Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                Collections.emptyList(), Collections.emptyList(), null, null, Collections.emptySet(), false, false, 1L, 0, 1
        );

        when(backendApiClient.get(eq("/api/v1/missions/" + missionId), org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<Object>>any(), eq(true)))
                .thenReturn(mission);
        when(backendApiClient.getCached(anyString(), org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<Object>>any(), anyBoolean()))
                .thenReturn(Collections.emptyList());

        // Anonymously access the mission detail page
        mockMvc.perform(get("/missions/" + missionId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("disabled=\"disabled\"")))
                .andExpect(content().string(containsString("payout-preference")))
                .andExpect(content().string(not(containsString("class=\"btn edit-participant-btn\""))));
    }

    @Test
    @WithMockUser(username = "admin-uuid", roles = "ADMIN")
    void missionDetail_AsAdmin_ShouldEnableRegisteredParticipantPayoutDropdown() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        UserDto user = new UserDto(userId, "TestUser", "Test User", "Test User", null, null, null, null, null, null, null, null, false, false, true, 1L, null);
        MissionParticipantDto participant = new MissionParticipantDto(participantId, user, null, null, null, null, null, null, null, PayoutPreference.PAYOUT, 1L);

        MissionDto mission = new MissionDto(
                missionId, "Test Mission", null, null, "PLANNED", null, null, null, null, null, false,
                Set.of(participant), Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                Collections.emptyList(), Collections.emptyList(), null, null, Collections.emptySet(), true, true, 1L, 0, 1
        );

        when(backendApiClient.get(eq("/api/v1/missions/" + missionId), org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<Object>>any(), eq(true)))
                .thenReturn(mission);
        when(backendApiClient.getCached(anyString(), org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<Object>>any(), anyBoolean()))
                .thenReturn(Collections.emptyList());

        // Authenticated as Admin
        mockMvc.perform(get("/missions/" + missionId))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("disabled=\"disabled\""))));
    }
    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void missionDetail_AsOtherUser_ShouldDisableRegisteredParticipantPayoutDropdown() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID(); 
        UUID participantId = UUID.randomUUID();

        UserDto user = new UserDto(userId, "TestUser", "Test User", "Test User", null, null, null, null, null, null, null, null, false, false, true, 1L, null);
        MissionParticipantDto participant = new MissionParticipantDto(participantId, user, null, null, null, null, null, null, null, PayoutPreference.PAYOUT, 1L);

        MissionDto mission = new MissionDto(
                missionId, "Test Mission", null, null, "PLANNED", null, null, null, null, null, false,
                Set.of(participant), Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                Collections.emptyList(), Collections.emptyList(), null, null, Collections.emptySet(), false, false, 1L, 0, 1
        );

        when(backendApiClient.get(eq("/api/v1/missions/" + missionId), org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<Object>>any(), eq(true)))
                .thenReturn(mission);
        when(backendApiClient.getCached(anyString(), org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<Object>>any(), anyBoolean()))
                .thenReturn(Collections.emptyList());

        // Authenticated as another user
        mockMvc.perform(get("/missions/" + missionId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("disabled=\"disabled\"")));
    }

    @Test
    @WithMockUser(username = "22222222-2222-2222-2222-222222222222")
    void missionDetail_AsSelf_ShouldEnableRegisteredParticipantPayoutDropdown() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID participantId = UUID.randomUUID();

        UserDto user = new UserDto(userId, "TestUser", "Test User", "Test User", null, null, null, null, null, null, null, null, false, false, true, 1L, null);
        MissionParticipantDto participant = new MissionParticipantDto(participantId, user, null, null, null, null, null, null, null, PayoutPreference.PAYOUT, 1L);

        MissionDto mission = new MissionDto(
                missionId, "Test Mission", null, null, "PLANNED", null, null, null, null, null, false,
                Set.of(participant), Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                Collections.emptyList(), Collections.emptyList(), null, null, Collections.emptySet(), false, false, 1L, 0, 1
        );

        when(backendApiClient.get(eq("/api/v1/missions/" + missionId), org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<Object>>any(), eq(true)))
                .thenReturn(mission);
        when(backendApiClient.getCached(anyString(), org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<Object>>any(), anyBoolean()))
                .thenReturn(Collections.emptyList());

        // Authenticated as the participant user
        String expectedUrl = "/missions/" + missionId + "/participants/" + participantId + "/payout-preference";
        mockMvc.perform(get("/missions/" + missionId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(expectedUrl)))
                .andExpect(content().string(not(containsString("data-payout-url=\"" + expectedUrl + "\" onchange=\"updatePayoutPreference(this)\" disabled=\"disabled\""))));
    }
}
