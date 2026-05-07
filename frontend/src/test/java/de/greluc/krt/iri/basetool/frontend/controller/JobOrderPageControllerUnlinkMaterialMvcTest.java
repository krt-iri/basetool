package de.greluc.krt.iri.basetool.frontend.controller;

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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC-Tests fuer den unlinkMaterial-POST-Pfad in {@link JobOrderPageController#unlinkMaterial}.
 *
 * <p>Testet, dass Logistiker/Officer/Admin ein Material aus einem Auftrag entlinken koennen:
 *
 * <ul>
 *   <li>Logistiker kann Material entlinken (success-Toast + Redirect).</li>
 *   <li>Einfacher Member ohne Logistiker-Rechte erhaelt 403 Forbidden.</li>
 *   <li>Backend-Fehler bei Logistiker → error-Toast + Redirect.</li>
 * </ul>
 */
@SpringBootTest
class JobOrderPageControllerUnlinkMaterialMvcTest {

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
    void unlinkMaterial_AsLogistician_ShouldCallBackendAndRedirectWithSuccessToast() throws Exception {
        // Given
        UUID orderId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();

        doReturn(null).when(backendApiClient).delete(
                eq("/api/v1/orders/" + orderId + "/materials/" + materialId), eq(Void.class));

        // When
        mockMvc.perform(post("/orders/" + orderId + "/materials/unlink")
                        .with(csrf())
                        .param("materialId", materialId.toString()))
                // Then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/" + orderId))
                .andExpect(flash().attribute("successToast", "orders.detail.material.unlink.success"));

        verify(backendApiClient).delete(
                eq("/api/v1/orders/" + orderId + "/materials/" + materialId), eq(Void.class));
    }

    @Test
    @WithMockUser(roles = {"MEMBER"})
    void unlinkMaterial_AsPlainMember_ShouldReturn403() throws Exception {
        // Given
        UUID orderId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();

        // When / Then
        mockMvc.perform(post("/orders/" + orderId + "/materials/unlink")
                        .with(csrf())
                        .param("materialId", materialId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"MEMBER", "LOGISTICIAN"})
    void unlinkMaterial_WhenBackendFails_ShouldRedirectWithErrorToast() throws Exception {
        // Given
        UUID orderId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();

        doThrow(new BackendServiceException("Internal Server Error", null, 500))
                .when(backendApiClient).delete(
                        eq("/api/v1/orders/" + orderId + "/materials/" + materialId), eq(Void.class));

        // When
        mockMvc.perform(post("/orders/" + orderId + "/materials/unlink")
                        .with(csrf())
                        .param("materialId", materialId.toString()))
                // Then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/" + orderId))
                .andExpect(flash().attribute("errorToast", "orders.detail.material.unlink.error"));
    }
}
