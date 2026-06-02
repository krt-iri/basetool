package de.greluc.krt.iri.basetool.frontend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalBlueprintDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
 * MVC-level test for {@link PersonalInventoryBlueprintsPageController}: verifies the Blueprints
 * sub-page renders for an authenticated user with the owned-blueprint list filled from the (mocked)
 * backend.
 */
@SpringBootTest
class PersonalInventoryBlueprintsPageControllerMvcTest {

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
  void view_shouldRenderBlueprintsView_whenAuthenticated() throws Exception {
    PersonalBlueprintDto bp =
        new PersonalBlueprintDto(
            UUID.randomUUID(),
            "arclight pistol",
            "Arclight Pistol",
            null,
            Instant.parse("2026-01-01T00:00:00Z"),
            "note",
            0L,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:00Z"));
    PageResponse<PersonalBlueprintDto> page =
        new PageResponse<>(List.of(bp), 0, 200, 1, 1, List.of());
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class))).thenReturn(page);

    mockMvc
        .perform(get("/personal-inventory/blueprints"))
        .andExpect(status().isOk())
        .andExpect(view().name("personal-inventory-blueprints"))
        .andExpect(model().attributeExists("blueprints"))
        // Regression guard (#363): the per-row id placeholder must survive Thymeleaf rendering
        // verbatim. The previous `__ID__` token was eaten by Thymeleaf preprocessing (`__...__`),
        // rendering `/blueprints/ID/recipe` and 400-ing every expand/edit/delete. We assert the
        // bare token (not the full path) because JS inlining escapes the slashes to `\/`.
        .andExpect(content().string(containsString("ID_PLACEHOLDER")));
  }
}
