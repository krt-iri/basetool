package de.greluc.krt.iri.basetool.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
public class UserAccessControlTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @org.junit.jupiter.api.BeforeEach
  public void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void testSearchUsers_Anonymous_Forbidden() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/search").param("query", "test"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void testSearchUsers_Authenticated_Forbidden() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/users/search")
                .param("query", "test")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GUEST"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void testSearchUsers_Officer_Allowed() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/users/search")
                .param("query", "test")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OFFICER"))))
        .andExpect(status().isOk());
  }
}
