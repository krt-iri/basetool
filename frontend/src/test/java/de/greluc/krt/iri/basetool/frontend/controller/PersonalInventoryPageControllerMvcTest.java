package de.greluc.krt.iri.basetool.frontend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalInventoryItemDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.List;
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

/**
 * MVC-level test for {@link PersonalInventoryPageController}: verifies that the user-area personal
 * inventory page renders for an authenticated user with model attributes filled from the (mocked)
 * backend.
 */
@SpringBootTest
class PersonalInventoryPageControllerMvcTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private BackendApiClient backendApiClient;

  @MockitoBean
  private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
      clientRegistrationRepository;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @WithMockUser
  void view_shouldRenderPersonalInventoryView_whenAuthenticated() throws Exception {
    // Given
    PageResponse<PersonalInventoryItemDto> empty =
        new PageResponse<>(List.of(), 0, 50, 0, 0, List.of());
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
        .thenReturn(empty);

    // When & Then
    mockMvc
        .perform(get("/personal-inventory"))
        .andExpect(status().isOk())
        .andExpect(view().name("personal-inventory"))
        .andExpect(model().attributeExists("personalInventoryForm"))
        .andExpect(model().attributeExists("items"));
  }
}
