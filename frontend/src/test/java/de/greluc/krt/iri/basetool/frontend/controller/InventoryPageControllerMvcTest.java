package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.AggregatedInventoryDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.InventoryItemDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.LocationReferenceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialReferenceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserReferenceDto;
import de.greluc.krt.iri.basetool.frontend.controller.InventoryPageController.GroupedInventoryDto;
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
import java.util.UUID;

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
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Einbuchen")));
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void viewAllInventory_AsMember_ShouldNotShowActions() throws Exception {
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/inventory/all"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Einbuchen"))));
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

    @Test
    @WithMockUser(roles = "MEMBER", username = "test-user-123")
    void viewAllInventory_ShouldRenderLocalStorageAttributes() throws Exception {
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/inventory/all"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"inventoryTable\"")))
                .andExpect(content().string(containsString("data-user-id=\"test-user-123\"")));
    }

    @Test
    @WithMockUser(roles = "MEMBER", username = "test-user-123")
    void viewMyInventory_ShouldRenderLocalStorageAttributes() throws Exception {
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/inventory/my"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"inventoryTable\"")))
                .andExpect(content().string(containsString("data-user-id=\"test-user-123\"")));
    }

    /**
     * Regression: a refinery order assigned to a (now non-active) mission produces an inventory item
     * whose mission is no longer returned by {@code /api/v1/missions/lookup}. The mission must still
     * be visible in the inventory association select via a fallback {@code <option selected>} so the
     * user can recognise the linked mission (consistent with the mission detail view).
     */
    @Test
    @WithMockUser(roles = "MEMBER", username = "test-user-123")
    void viewMyInventory_WhenItemMissionNotInLookup_ShouldRenderFallbackOption() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        String missionName = "Op Sundown (archived)";

        InventoryItemDto item = new InventoryItemDto(
                itemId,
                new UserReferenceDto(userId, "tester", "Tester", "Tester", null),
                new MaterialReferenceDto(materialId, "Quantanium", "SCU"),
                new LocationReferenceDto(locationId, "ARC-L1"),
                90, 10.0, false,
                null, null,
                missionId, missionName,
                null, 1L);
        GroupedInventoryDto group = new GroupedInventoryDto(
                new MaterialReferenceDto(materialId, "Quantanium", "SCU"),
                10.0, 90.0, 90, List.of(item));

        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
                .thenAnswer(inv -> {
                    String url = inv.getArgument(0);
                    if (url.contains("/inventory/my-inventory/grouped")) {
                        return List.of(group);
                    }
                    return Collections.emptyList();
                });
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/inventory/my"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"" + missionId + "\"")))
                .andExpect(content().string(containsString(missionName)))
                .andExpect(content().string(containsString("selected=\"selected\"")));
    }

    /**
     * Same regression as {@link #viewMyInventory_WhenItemMissionNotInLookup_ShouldRenderFallbackOption()}
     * for the logistician/admin view ({@code inventory-admin.html}).
     */
    @Test
    @WithMockUser(roles = "LOGISTICIAN", username = "logi-user")
    void viewAllInventory_WhenItemMissionNotInLookup_ShouldRenderFallbackOption() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        String missionName = "Op Sundown (archived)";

        InventoryItemDto item = new InventoryItemDto(
                itemId,
                new UserReferenceDto(userId, "tester", "Tester", "Tester", null),
                new MaterialReferenceDto(materialId, "Quantanium", "SCU"),
                new LocationReferenceDto(locationId, "ARC-L1"),
                90, 10.0, false,
                null, null,
                missionId, missionName,
                null, 1L);
        GroupedInventoryDto group = new GroupedInventoryDto(
                new MaterialReferenceDto(materialId, "Quantanium", "SCU"),
                10.0, 90.0, 90, List.of(item));

        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
                .thenAnswer(inv -> {
                    String url = inv.getArgument(0);
                    if (url.contains("/inventory/all/grouped")) {
                        return List.of(group);
                    }
                    return Collections.emptyList();
                });
        when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/inventory/all"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"" + missionId + "\"")))
                .andExpect(content().string(containsString(missionName)))
                .andExpect(content().string(containsString("selected=\"selected\"")));
    }
}
