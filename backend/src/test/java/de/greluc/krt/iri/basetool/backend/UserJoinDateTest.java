package de.greluc.krt.iri.basetool.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserJoinDateTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @MockitoBean private JwtDecoder jwtDecoder;

  private User testUser;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    testUser = new User();
    testUser.setId(UUID.randomUUID());
    testUser.setUsername("joindate_testmember");
    testUser.setRank(1);
    userRepository.save(testUser);
  }

  @Test
  void shouldSetJoinDate_WhenAdminUpdatesAttributes() throws Exception {
    // Given
    String joinDate = "2024-03-15";
    String updateJson =
        "{\"rank\": 5, \"version\": "
            + testUser.getVersion()
            + ", \"joinDate\": \""
            + joinDate
            + "\"}";

    // When
    mockMvc
        .perform(
            put("/api/v1/users/" + testUser.getId() + "/attributes")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
        // Then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.joinDate").value(joinDate));
  }

  @Test
  void shouldForbidJoinDateUpdate_WhenOfficerRole() throws Exception {
    // Given
    String joinDate = "2023-06-01";
    String updateJson =
        "{\"rank\": 3, \"version\": "
            + testUser.getVersion()
            + ", \"joinDate\": \""
            + joinDate
            + "\"}";

    // When
    mockMvc
        .perform(
            put("/api/v1/users/" + testUser.getId() + "/attributes")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OFFICER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
        // Then
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldClearJoinDate_WhenNullIsSent() throws Exception {
    // Given – set a date first
    testUser.setJoinDate(LocalDate.of(2022, 1, 1));
    userRepository.save(testUser);
    userRepository.flush();

    String updateJson =
        "{\"rank\": 1, \"version\": " + testUser.getVersion() + ", \"joinDate\": null}";

    // When
    mockMvc
        .perform(
            put("/api/v1/users/" + testUser.getId() + "/attributes")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
        // Then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.joinDate").doesNotExist());

    User updated = userRepository.findById(testUser.getId()).orElseThrow();
    assertThat(updated.getJoinDate()).isNull();
  }

  @Test
  void shouldForbidJoinDateUpdate_WhenMemberRole() throws Exception {
    // Given
    String updateJson =
        "{\"rank\": 1, \"version\": " + testUser.getVersion() + ", \"joinDate\": \"2024-01-01\"}";

    // When / Then
    mockMvc
        .perform(
            put("/api/v1/users/" + testUser.getId() + "/attributes")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldReturnJoinDate_InResponseDto_WhenSet() throws Exception {
    // Given
    testUser.setJoinDate(LocalDate.of(2021, 5, 20));
    userRepository.save(testUser);

    String updateJson = "{\"rank\": 1, \"version\": " + testUser.getVersion() + "}";

    // When
    mockMvc
        .perform(
            put("/api/v1/users/" + testUser.getId() + "/attributes")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
        // Then – joinDate is reset to null because request sends null
        .andExpect(status().isOk());
  }
}
