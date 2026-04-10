package de.greluc.krt.iri.basetool.frontend;

import de.greluc.krt.iri.basetool.frontend.controller.MissionPageController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class MissionFrontendSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private WebClient webClient;

    @MockitoBean
    private WebClient publicWebClient;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;
    
    @SuppressWarnings("rawtypes")
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @SuppressWarnings("rawtypes")
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        // Standard mocking for publicWebClient used in listMissions
        when(publicWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(String.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(new de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse<>(Collections.emptyList(), 0, 20, 0, 0, Collections.emptyList())));
    }

    @Test
    @WithAnonymousUser
    void testMissionsList_Anonymous_ShouldNotSeeCreateButton() throws Exception {
        mockMvc.perform(get("/missions"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/missions/new"))))
                .andExpect(content().string(not(containsString("Create New"))))
                .andExpect(content().string(not(containsString("Neue Mission"))));
    }

    @Test
    @WithMockUser(roles = "SQUADRON_MEMBER")
    void testMissionsList_User_ShouldSeeCreateButton() throws Exception {
        mockMvc.perform(get("/missions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/missions/new")));
    }

    @Test
    @WithMockUser(roles = "MISSION_MANAGER")
    void testMissionsList_MissionManager_ShouldSeeCreateButton() throws Exception {
        mockMvc.perform(get("/missions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/missions/new")));
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void testMissionsList_Officer_ShouldSeeCreateButton() throws Exception {
        mockMvc.perform(get("/missions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/missions/new")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testMissionsList_Admin_ShouldSeeCreateButton() throws Exception {
        mockMvc.perform(get("/missions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/missions/new")));
    }
}
