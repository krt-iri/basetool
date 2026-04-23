package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import jakarta.servlet.http.Cookie;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class JobOrderPageCookieTest {

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
    @WithMockUser
    void viewOrders_WithoutCookie_ShouldUseDefaultAndSetNoNewCookie() throws Exception {
        when(backendApiClient.get(eq("/api/v1/orders?size=1000&sort=priority,asc&status=OPEN,IN_PROGRESS"), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 0L, 0, List.of()));
                
        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("orders_filter_status"));
                
        verify(backendApiClient).get(eq("/api/v1/orders?size=1000&sort=priority,asc&status=OPEN,IN_PROGRESS"), any(org.springframework.core.ParameterizedTypeReference.class));
    }

    @Test
    @WithMockUser
    void viewOrders_WithValidCookie_ShouldUseCookie() throws Exception {
        when(backendApiClient.get(eq("/api/v1/orders?size=1000&sort=priority,asc&status=COMPLETED"), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 0L, 0, List.of()));
                
        mockMvc.perform(get("/orders").cookie(new Cookie("orders_filter_status", "COMPLETED")))
                .andExpect(status().isOk());
                
        verify(backendApiClient).get(eq("/api/v1/orders?size=1000&sort=priority,asc&status=COMPLETED"), any(org.springframework.core.ParameterizedTypeReference.class));
    }

    @Test
    @WithMockUser
    void viewOrders_WithInvalidOldCookie_ShouldFallbackToDefault() throws Exception {
        when(backendApiClient.get(eq("/api/v1/orders?size=1000&sort=priority,asc&status=OPEN,IN_PROGRESS"), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 0L, 0, List.of()));
                
        mockMvc.perform(get("/orders").cookie(new Cookie("orders_filter_status", "OPEN_IN_PROGRESS")))
                .andExpect(status().isOk());
                
        verify(backendApiClient).get(eq("/api/v1/orders?size=1000&sort=priority,asc&status=OPEN,IN_PROGRESS"), any(org.springframework.core.ParameterizedTypeReference.class));
    }

    @Test
    @WithMockUser
    void viewOrders_WithNewCookieFormat_ShouldUseCookie() throws Exception {
        when(backendApiClient.get(eq("/api/v1/orders?size=1000&sort=priority,asc&status=OPEN,IN_PROGRESS"), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 0L, 0, List.of()));
                
        mockMvc.perform(get("/orders").cookie(new Cookie("orders_filter_status", "OPEN-IN_PROGRESS")))
                .andExpect(status().isOk());
                
        verify(backendApiClient).get(eq("/api/v1/orders?size=1000&sort=priority,asc&status=OPEN,IN_PROGRESS"), any(org.springframework.core.ParameterizedTypeReference.class));
    }

    @Test
    @WithMockUser
    void viewOrders_WithQueryParam_ShouldSetNewCookie() throws Exception {
        when(backendApiClient.get(eq("/api/v1/orders?size=1000&sort=priority,asc&status=COMPLETED"), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 0L, 0, List.of()));
                
        mockMvc.perform(get("/orders").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("orders_filter_status", "COMPLETED"));
                
        verify(backendApiClient).get(eq("/api/v1/orders?size=1000&sort=priority,asc&status=COMPLETED"), any(org.springframework.core.ParameterizedTypeReference.class));
    }
}
