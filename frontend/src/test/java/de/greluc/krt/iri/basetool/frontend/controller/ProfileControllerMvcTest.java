package de.greluc.krt.iri.basetool.frontend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ProfileControllerMvcTest {

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
  void profile_ShouldSetMonthsInSquadron_WhenJoinDateIsPresent() throws Exception {
    // Given
    LocalDate joinDate = LocalDate.now().minusMonths(14);
    long expectedMonths = ChronoUnit.MONTHS.between(joinDate, LocalDate.now());

    when(backendApiClient.get(eq("/api/v1/users/me"), any(ParameterizedTypeReference.class)))
        .thenReturn(
            Map.of(
                "rank", "Pilot",
                "description", "Test",
                "displayName", "TestUser",
                "version", 1L,
                "joinDate", joinDate.toString()));

    // When & Then
    mockMvc
        .perform(get("/profile").with(oidcLogin()))
        .andExpect(status().isOk())
        .andExpect(view().name("profile"))
        .andExpect(model().attribute("monthsInSquadron", expectedMonths));
  }

  @Test
  void profile_ShouldNotSetMonthsInSquadron_WhenJoinDateIsAbsent() throws Exception {
    // Given
    when(backendApiClient.get(eq("/api/v1/users/me"), any(ParameterizedTypeReference.class)))
        .thenReturn(
            Map.of(
                "rank", "Pilot",
                "description", "Test",
                "displayName", "TestUser",
                "version", 1L));

    // When & Then
    mockMvc
        .perform(get("/profile").with(oidcLogin()))
        .andExpect(status().isOk())
        .andExpect(view().name("profile"))
        .andExpect(model().attributeDoesNotExist("monthsInSquadron"));
  }

  @Test
  void profile_ShouldNotSetMonthsInSquadron_WhenJoinDateIsNull() throws Exception {
    // Given
    Map<String, Object> userMap = new HashMap<>();
    userMap.put("rank", "Pilot");
    userMap.put("description", "Test");
    userMap.put("displayName", "TestUser");
    userMap.put("version", 1L);
    userMap.put("joinDate", null);

    when(backendApiClient.get(eq("/api/v1/users/me"), any(ParameterizedTypeReference.class)))
        .thenReturn(userMap);

    // When & Then
    mockMvc
        .perform(get("/profile").with(oidcLogin()))
        .andExpect(status().isOk())
        .andExpect(view().name("profile"))
        .andExpect(model().attributeDoesNotExist("monthsInSquadron"));
  }
}
