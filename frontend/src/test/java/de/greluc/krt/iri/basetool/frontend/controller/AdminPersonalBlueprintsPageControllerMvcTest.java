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
import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
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
 * MVC-level test for {@link AdminPersonalBlueprintsPageController}: the admin Blueprints page
 * renders for an ADMIN and is forbidden for a non-admin (the {@code hasRole('ADMIN')} gate).
 */
@SpringBootTest
class AdminPersonalBlueprintsPageControllerMvcTest {

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
  @WithMockUser(roles = "ADMIN")
  void view_rendersForAdmin_withUserPicker() throws Exception {
    PageResponse<UserDto> users = new PageResponse<>(List.of(), 0, 1000, 0, 0, List.of());
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
        .thenReturn(users);

    mockMvc
        .perform(get("/admin/personal-blueprints"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/personal-blueprints"))
        .andExpect(model().attributeExists("users"))
        .andExpect(model().attributeExists("blueprints"))
        .andExpect(model().attribute("adminMode", Boolean.TRUE));
  }

  @Test
  @WithMockUser(roles = "SQUADRON_MEMBER")
  void view_forbiddenForNonAdmin() throws Exception {
    mockMvc.perform(get("/admin/personal-blueprints")).andExpect(status().isForbidden());
  }
}
