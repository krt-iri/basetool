package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalInventoryItemDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Verifies admin role enforcement for {@link AdminPersonalInventoryPageController}: a regular
 * user must be denied access (403), while an ADMIN gets the admin view rendered. Backend
 * calls are mocked because this test focuses on routing and security, not backend behavior.
 */
@SpringBootTest
class AdminPersonalInventoryPageControllerMvcTest {

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
    @WithMockUser(roles = "USER")
    void view_shouldDenyAccess_whenUserIsNotAdmin() throws Exception {
        mockMvc.perform(get("/admin/personal-inventory"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void view_shouldRenderAdminView_whenUserIsAdmin() throws Exception {
        // Given
        PageResponse<UserDto> users = new PageResponse<>(List.of(), 0, 1000, 0, 1, List.of());
        PageResponse<PersonalInventoryItemDto> empty = new PageResponse<>(List.of(), 0, 50, 0, 0, List.of());
        when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
                .thenReturn(users)
                .thenReturn(empty);

        // When & Then
        mockMvc.perform(get("/admin/personal-inventory"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/personal-inventory"));
    }
}
