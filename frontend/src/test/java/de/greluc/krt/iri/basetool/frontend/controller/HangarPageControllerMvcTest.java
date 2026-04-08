package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.LocationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.ShipDto;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
class HangarPageControllerMvcTest {

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
    @WithMockUser
    void viewHangar_ShouldRenderHangarView() throws Exception {
        // Given
        PageResponse<ShipDto> ships = new PageResponse<>(List.of(), 0, 10, 0, 1, List.of());
        
        ShipTypeDto shipType = new ShipTypeDto(UUID.randomUUID(), "Avenger Titan", null, "Titan", 0, false);
        PageResponse<ShipTypeDto> shipTypes = new PageResponse<>(List.of(shipType), 0, 10, 1, 1, List.of());
        
        LocationDto location = new LocationDto(UUID.randomUUID(), "Area18", "City", false, null);
        PageResponse<LocationDto> locations = new PageResponse<>(List.of(location), 0, 10, 1, 1, List.of());

        when(backendApiClient.get(eq("/api/v1/hangar/my-ships?size=1000"), any(ParameterizedTypeReference.class)))
                .thenReturn(ships);
        when(backendApiClient.getCached(eq("/api/v1/ship-types?size=1000"), any(ParameterizedTypeReference.class)))
                .thenReturn(shipTypes);
        when(backendApiClient.getCached(eq("/api/v1/locations?size=1000"), any(ParameterizedTypeReference.class)))
                .thenReturn(locations);

        // When & Then
        mockMvc.perform(get("/hangar"))
                .andExpect(status().isOk())
                .andExpect(view().name("hangar"));
    }
}
