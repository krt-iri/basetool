package de.greluc.krt.iri.basetool.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.backend.model.StarSystem;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.StarSystemRepository;
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
class StarSystemTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Autowired private StarSystemRepository starSystemRepository;

  @Autowired private UserRepository userRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean private JwtDecoder jwtDecoder;

  private User officerUser;
  private User guestUser;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    officerUser = new User();
    officerUser.setId(UUID.randomUUID());
    officerUser.setUsername("officerSystem");
    userRepository.save(officerUser);

    guestUser = new User();
    guestUser.setId(UUID.randomUUID());
    guestUser.setUsername("guestSystem");
    userRepository.save(guestUser);
  }

  @Test
  void testCreateStarSystem_Officer_Allowed() throws Exception {
    StarSystem system = new StarSystem();
    system.setName("Stanton");
    system.setDescription("A corporate owned system.");

    String response =
        mockMvc
            .perform(
                post("/api/v1/star-systems")
                    .with(
                        jwt()
                            .jwt(builder -> builder.subject(officerUser.getId().toString()))
                            .authorities(
                                new SimpleGrantedAuthority("ROLE_OFFICER"),
                                new SimpleGrantedAuthority("USER_MANAGE"),
                                new SimpleGrantedAuthority("MISSION_MANAGE"),
                                new SimpleGrantedAuthority("HANGAR_MANAGE"),
                                new SimpleGrantedAuthority("REFINERY_MANAGE")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(system)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    StarSystem saved = objectMapper.readValue(response, StarSystem.class);
    assertNotNull(saved.getId());
    assertEquals("Stanton", saved.getName());
  }

  @Test
  void testCreateStarSystem_Guest_Forbidden() throws Exception {
    StarSystem system = new StarSystem();
    system.setName("Pyro");

    mockMvc
        .perform(
            post("/api/v1/star-systems")
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(guestUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_GUEST")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(system)))
        .andExpect(status().isForbidden());
  }

  @Test
  void testUpdateStarSystem_Officer_Allowed() throws Exception {
    StarSystem system = new StarSystem();
    system.setName("Stanton");
    system = starSystemRepository.save(system);

    system.setName("Stanton System");

    mockMvc
        .perform(
            put("/api/v1/star-systems/" + system.getId())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_OFFICER"),
                            new SimpleGrantedAuthority("USER_MANAGE"),
                            new SimpleGrantedAuthority("MISSION_MANAGE"),
                            new SimpleGrantedAuthority("HANGAR_MANAGE"),
                            new SimpleGrantedAuthority("REFINERY_MANAGE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(system)))
        .andExpect(status().isOk());

    StarSystem updated = starSystemRepository.findById(system.getId()).orElseThrow();
    assertEquals("Stanton System", updated.getName());
  }

  @Test
  void testDeleteStarSystem_Officer_Allowed() throws Exception {
    StarSystem system = new StarSystem();
    system.setName("Nyx");
    system = starSystemRepository.save(system);

    mockMvc
        .perform(
            delete("/api/v1/star-systems/" + system.getId())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_OFFICER"),
                            new SimpleGrantedAuthority("USER_MANAGE"),
                            new SimpleGrantedAuthority("MISSION_MANAGE"),
                            new SimpleGrantedAuthority("HANGAR_MANAGE"),
                            new SimpleGrantedAuthority("REFINERY_MANAGE"))))
        .andExpect(status().isOk());

    assertTrue(starSystemRepository.findById(system.getId()).isEmpty());
  }
}
