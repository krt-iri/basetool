package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.AggregatedInventoryDto;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@SpringBootTest
@ActiveProfiles("test")
class InventoryPageControllerMvcTest {

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
    @WithMockUser(roles = "MEMBER")
    void viewAggregatedInventory_AsMember_ShouldShowPage() throws Exception {
        PageResponse<AggregatedInventoryDto> page = new PageResponse<>(List.of(), 0, 10, 0, 1, Collections.emptyList());
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(page);
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/inventory"))
                .andExpect(status().isOk())
                .andExpect(view().name("inventory-index"))
                .andExpect(model().attributeExists("aggregated"));
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void viewAllInventory_AsMember_ShouldShowPage() throws Exception {
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/inventory/all"))
                .andExpect(status().isOk())
                .andExpect(view().name("inventory-admin"))
                .andExpect(model().attributeExists("groupedItems"));
    }

    @Test
    @WithMockUser(roles = "LOGISTICIAN")
    void viewAllInventory_AsLogistician_ShouldShowActions() throws Exception {
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/inventory/all"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Neuen Eintrag erfassen")));
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void viewAllInventory_AsMember_ShouldNotShowActions() throws Exception {
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/inventory/all"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Neuen Eintrag erfassen"))));
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void viewAllInventory_ShouldRenderBookOutButtonWithDynamicLabels() throws Exception {
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/inventory/all"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"bookOutSubmitBtn\"")))
                .andExpect(content().string(containsString("data-text-discard=\"Ausbuchen\"")))
                .andExpect(content().string(containsString("data-text-transfer=\"Umbuchen\"")))
                .andExpect(content().string(containsString("data-text-sell=\"Verkaufen\"")));
    }
}
