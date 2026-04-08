package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class MissionManagerButtonVisibilityTest {

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
    @WithMockUser(roles = "MISSION_MANAGER")
    void listMissions_AsMissionManager_ShouldShowCreateButton() throws Exception {
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class), anyBoolean()))
                .thenReturn(new PageResponse<>(Collections.emptyList(), 0, 10, 0, 0, Collections.emptyList()));
        
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class), anyBoolean()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/missions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/missions/new\"")));
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void listMissions_AsMember_ShouldShowCreateButton() throws Exception {
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class), anyBoolean()))
                .thenReturn(new PageResponse<>(Collections.emptyList(), 0, 10, 0, 0, Collections.emptyList()));
        
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class), anyBoolean()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/missions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/missions/new\"")));
    }

    @Test
    void listMissions_AsAnonymous_ShouldNotShowCreateButton() throws Exception {
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class), anyBoolean()))
                .thenReturn(new PageResponse<>(Collections.emptyList(), 0, 10, 0, 0, Collections.emptyList()));
        
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class), anyBoolean()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/missions"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/missions/new\""))));
    }
}
