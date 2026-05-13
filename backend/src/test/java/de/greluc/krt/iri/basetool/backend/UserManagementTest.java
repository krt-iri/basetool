package de.greluc.krt.iri.basetool.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
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
class UserManagementTest {

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
    testUser.setUsername("testmember");
    testUser.setRank(1);
    userRepository.save(testUser);
  }

  @Test
  void testGetAllUsers_Admin_Allowed() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.username=='testmember')]").exists());
  }

  @Test
  void testGetAllUsers_Guest_Forbidden() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GUEST"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void testGetAllUsers_Officer_Allowed() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/users")
                .with(
                    jwt()
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_OFFICER"),
                            new SimpleGrantedAuthority("USER_MANAGE"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.username=='testmember')]").exists());
  }

  @Test
  void testUpdateUserAttributes_Admin_Allowed() throws Exception {
    String updateJson =
        "{\"rank\": 5, \"description\": \"Promoted\", \"version\": " + testUser.getVersion() + "}";

    mockMvc
        .perform(
            put("/api/v1/users/" + testUser.getId() + "/attributes")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rank").value(5))
        .andExpect(jsonPath("$.description").value("Promoted"));
  }
}
