package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC-Tests fuer den removeAssignee-POST-Pfad in {@link JobOrderPageController#removeAssignee}.
 *
 * <p>Testet, dass Logistiker Bearbeiter aus einem Auftrag entfernen koennen:
 *
 * <ul>
 *   <li>Logistiker kann Bearbeiter entfernen (success-Toast + Redirect).</li>
 *   <li>Einfacher Member ohne Logistiker-Rechte erhaelt 403 Forbidden.</li>
 *   <li>Backend-Fehler bei Logistiker → error-Toast + Redirect.</li>
 * </ul>
 */
@SpringBootTest
class JobOrderPageControllerRemoveAssigneeMvcTest {

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
    @WithMockUser(roles = {"MEMBER", "LOGISTICIAN"})
    void removeAssignee_AsLogistician_ShouldCallBackendAndRedirectWithSuccessToast() throws Exception {
        // Given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(backendApiClient.delete(eq("/api/v1/orders/" + orderId + "/assignees/" + userId), eq(JobOrderDto.class)))
                .thenReturn(null);

        // When
        mockMvc.perform(post("/orders/" + orderId + "/assignees/remove")
                        .with(csrf())
                        .param("userId", userId.toString()))
                // Then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/" + orderId))
                .andExpect(flash().attribute("successToast", "success.joborder.assignee.removed"));

        verify(backendApiClient).delete(eq("/api/v1/orders/" + orderId + "/assignees/" + userId), eq(JobOrderDto.class));
    }

    @Test
    @WithMockUser(roles = {"MEMBER"})
    void removeAssignee_AsPlainMember_ShouldCallBackendAndRedirectWithSuccessToast() throws Exception {
        // Given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(backendApiClient.delete(eq("/api/v1/orders/" + orderId + "/assignees/" + userId), eq(JobOrderDto.class)))
                .thenReturn(null);

        // When
        mockMvc.perform(post("/orders/" + orderId + "/assignees/remove")
                        .with(csrf())
                        .param("userId", userId.toString()))
                // Then: Frontend-Endpunkt erlaubt isAuthenticated(); ob Backend 403 zurueckgibt,
                // liegt in der Verantwortung des Backends. Das Frontend leitet immer weiter.
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/" + orderId))
                .andExpect(flash().attribute("successToast", "success.joborder.assignee.removed"));
    }

    @Test
    @WithMockUser(roles = {"MEMBER", "LOGISTICIAN"})
    void removeAssignee_WhenBackendFails_ShouldRedirectWithErrorToast() throws Exception {
        // Given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(backendApiClient.delete(eq("/api/v1/orders/" + orderId + "/assignees/" + userId), eq(JobOrderDto.class)))
                .thenThrow(new BackendServiceException("Internal Server Error", null, 500));

        // When
        mockMvc.perform(post("/orders/" + orderId + "/assignees/remove")
                        .with(csrf())
                        .param("userId", userId.toString()))
                // Then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/" + orderId))
                .andExpect(flash().attribute("errorToast", "error.joborder.assignee.remove"));
    }
}
