package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderHandoverCreateDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderHandoverDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC-Tests fuer den Handover-POST-Pfad in {@link JobOrderPageController#createHandover}.
 *
 * <p>Dient als zusaetzliche Absicherung des in der Backend-Korrektur behobenen Optimistic-Locking-
 * Bugs auf der Frontend-Seite: das Frontend leitet Erfolgs- wie Fehlerantworten unveraendert via
 * Flash-Toast + Redirect an den Browser weiter. Die Tests verifizieren:
 *
 * <ul>
 *   <li>Erfolgreiche Uebergabe → DTO wird korrekt gemappt, success-Toast, Redirect auf Detailseite.</li>
 *   <li>Backend-409 (z. B. Optimistic-Lock-Konflikt) → kein 5xx, error-Toast, Redirect auf Detailseite.</li>
 *   <li>Leeres Items-Set → kein Backend-Call, error-Toast, Redirect auf Detailseite.</li>
 * </ul>
 */
@SpringBootTest
class JobOrderPageControllerHandoverMvcTest {

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
    void createHandover_WithValidItems_ShouldMapDtoAndRedirectWithSuccessToast() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID inventoryItemId = UUID.randomUUID();

        when(backendApiClient.post(eq("/api/v1/orders/" + orderId + "/handovers"), any(), eq(JobOrderHandoverDto.class)))
                .thenReturn(null);

        mockMvc.perform(post("/orders/" + orderId + "/handovers")
                        .with(csrf())
                        .param("handoverTime", "2026-04-29T18:00:00.000Z")
                        .param("recipientHandle", "RecipientUser")
                        .param("recipientSquadron", "Alpha")
                        .param("items[0].inventoryItemId", inventoryItemId.toString())
                        .param("items[0].amount", "1.5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/" + orderId))
                .andExpect(flash().attribute("successToast", "success.joborder.handover"));

        ArgumentCaptor<JobOrderHandoverCreateDto> dtoCaptor = ArgumentCaptor.forClass(JobOrderHandoverCreateDto.class);
        verify(backendApiClient).post(eq("/api/v1/orders/" + orderId + "/handovers"), dtoCaptor.capture(), eq(JobOrderHandoverDto.class));

        JobOrderHandoverCreateDto sent = dtoCaptor.getValue();
        assertThat(sent.recipientHandle()).isEqualTo("RecipientUser");
        assertThat(sent.recipientSquadron()).isEqualTo("Alpha");
        assertThat(sent.items()).hasSize(1);
        assertThat(sent.items().getFirst().inventoryItemId()).isEqualTo(inventoryItemId);
        assertThat(sent.items().getFirst().amount()).isEqualTo(1.5);
        assertThat(sent.handoverTime()).isEqualTo(java.time.Instant.parse("2026-04-29T18:00:00.000Z"));
    }

    @Test
    @WithMockUser(roles = {"MEMBER", "LOGISTICIAN"})
    void createHandover_WhenBackendReturns409_ShouldRedirectWithErrorToast() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID inventoryItemId = UUID.randomUUID();

        when(backendApiClient.post(eq("/api/v1/orders/" + orderId + "/handovers"), any(), eq(JobOrderHandoverDto.class)))
                .thenThrow(new BackendServiceException("Conflict", null, 409));

        mockMvc.perform(post("/orders/" + orderId + "/handovers")
                        .with(csrf())
                        .param("handoverTime", "2026-04-29T18:00:00.000Z")
                        .param("recipientHandle", "RecipientUser")
                        .param("items[0].inventoryItemId", inventoryItemId.toString())
                        .param("items[0].amount", "1.5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/" + orderId))
                .andExpect(flash().attribute("errorToast", "error.joborder.handover.failed"));
    }

    @Test
    @WithMockUser(roles = {"MEMBER", "LOGISTICIAN"})
    void createHandover_WithNoItems_ShouldShortCircuit_NotCallBackend_AndShowErrorToast() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/orders/" + orderId + "/handovers")
                        .with(csrf())
                        .param("handoverTime", "2026-04-29T18:00:00.000Z")
                        .param("recipientHandle", "RecipientUser"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/" + orderId))
                .andExpect(flash().attribute("errorToast", "error.joborder.handover.noitems"));

        verify(backendApiClient, never()).post(eq("/api/v1/orders/" + orderId + "/handovers"), any(), eq(JobOrderHandoverDto.class));
    }
}
