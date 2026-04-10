package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.ShipTypeDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ProfitCalculationPageControllerMvcTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

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
    @WithMockUser(roles = "SQUADRON_MEMBER")
    void showProfitCalculationPage_ShouldSetDefaultShipId_WhenC2IsPresent() throws Exception {
        // Given
        UUID c2Id = UUID.randomUUID();
        ShipTypeDto c2 = new ShipTypeDto(c2Id, "C2 Hercules Starlifter", null, "C2", 696, false);
        PageResponse<ShipTypeDto> shipTypes = new PageResponse<>(List.of(c2), 0, 10, 1, 1, List.of());
        
        PageResponse<Map<String, Object>> terminals = new PageResponse<>(List.of(Map.of("starSystemName", "Stanton")), 0, 10, 1, 1, List.of());

        when(backendApiClient.get(eq("/api/v1/ship-types?size=1000&sort=name,asc"), any(ParameterizedTypeReference.class)))
                .thenReturn(shipTypes);
        when(backendApiClient.get(eq("/api/v1/terminals?size=10000"), any(ParameterizedTypeReference.class)))
                .thenReturn(terminals);

        // When & Then
        mockMvc.perform(get("/materials/profit-calculation"))
                .andExpect(status().isOk())
                .andExpect(view().name("materials-profit-calculation"))
                .andExpect(model().attribute("defaultShipId", c2Id))
                .andExpect(model().attributeExists("shipTypes"))
                .andExpect(model().attributeExists("starSystems"));
    }

    @Test
    @WithMockUser(roles = "SQUADRON_MEMBER")
    void showProfitCalculationPage_ShouldNotSetDefaultShipId_WhenC2IsMissing() throws Exception {
        // Given
        ShipTypeDto titan = new ShipTypeDto(UUID.randomUUID(), "Avenger Titan", null, "Titan", 8, false);
        PageResponse<ShipTypeDto> shipTypes = new PageResponse<>(List.of(titan), 0, 10, 1, 1, List.of());
        
        PageResponse<Map<String, Object>> terminals = new PageResponse<>(List.of(), 0, 10, 0, 1, List.of());

        when(backendApiClient.get(eq("/api/v1/ship-types?size=1000&sort=name,asc"), any(ParameterizedTypeReference.class)))
                .thenReturn(shipTypes);
        when(backendApiClient.get(eq("/api/v1/terminals?size=10000"), any(ParameterizedTypeReference.class)))
                .thenReturn(terminals);

        // When & Then
        mockMvc.perform(get("/materials/profit-calculation"))
                .andExpect(status().isOk())
                .andExpect(view().name("materials-profit-calculation"))
                .andExpect(model().attributeDoesNotExist("defaultShipId"));
    }

    @Test
    @WithMockUser(roles = "SQUADRON_MEMBER")
    void showProfitCalculationPage_ShouldFilterShipsWithZeroScu() throws Exception {
        // Given
        UUID titanId = UUID.randomUUID();
        ShipTypeDto gladius = new ShipTypeDto(UUID.randomUUID(), "Aegis Gladius", null, "Gladius", 0, false);
        ShipTypeDto titan = new ShipTypeDto(titanId, "Avenger Titan", null, "Titan", 8, false);
        ShipTypeDto noScu = new ShipTypeDto(UUID.randomUUID(), "No SCU", null, "No SCU", null, false);
        
        PageResponse<ShipTypeDto> shipTypes = new PageResponse<>(List.of(gladius, titan, noScu), 0, 10, 3, 1, List.of());
        
        PageResponse<Map<String, Object>> terminals = new PageResponse<>(List.of(), 0, 10, 0, 1, List.of());

        when(backendApiClient.get(eq("/api/v1/ship-types?size=1000&sort=name,asc"), any(ParameterizedTypeReference.class)))
                .thenReturn(shipTypes);
        when(backendApiClient.get(eq("/api/v1/terminals?size=10000"), any(ParameterizedTypeReference.class)))
                .thenReturn(terminals);

        // When & Then
        mockMvc.perform(get("/materials/profit-calculation"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("shipTypes", List.of(titan)));
    }
}
