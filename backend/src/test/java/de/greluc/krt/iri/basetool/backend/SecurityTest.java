package de.greluc.krt.iri.basetool.backend;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.backend.filter.RateLimitingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class SecurityTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Autowired private RateLimitingFilter rateLimitingFilter;

  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void testCorsHeaders() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/missions")
                .header("Origin", "http://localhost:8080")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().exists("Access-Control-Allow-Origin"));
  }

  @Test
  void testCorsHeaders_ForbiddenOrigin() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/missions")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }

  @Test
  void testSecurityHeaders() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().exists("Content-Security-Policy"));
  }

  @Test
  void testRateLimiting() throws Exception {
    // First request should pass
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }

  @Test
  void testAnonymousAccessToMissions() throws Exception {
    mockMvc.perform(get("/api/v1/missions")).andExpect(status().isOk());
  }

  @Test
  void testAuthenticatedAccessToMissions() throws Exception {
    org.springframework.security.oauth2.jwt.Jwt jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", java.util.UUID.randomUUID().toString())
            .claim("preferred_username", "testuser")
            .build();

    mockMvc
        .perform(
            get("/api/v1/missions")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt)))
        .andExpect(status().isOk());
  }

  @Test
  void testAnonymousAccessToLocations() throws Exception {
    mockMvc.perform(get("/api/v1/locations")).andExpect(status().isOk());
  }

  @Test
  void testAnonymousAccessToJobTypes() throws Exception {
    mockMvc.perform(get("/api/v1/job-types")).andExpect(status().isOk());
  }

  @Test
  void testAuthenticatedAccessWithInvalidSub() throws Exception {
    org.springframework.security.oauth2.jwt.Jwt jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "not-a-uuid")
            .claim("preferred_username", "testuser")
            .build();

    mockMvc
        .perform(
            get("/api/v1/missions")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt)))
        .andExpect(status().isOk());
  }

  @Test
  void testAuthenticatedAccessWithNullSub() throws Exception {
    // We create a JWT without a sub claim
    org.springframework.security.oauth2.jwt.Jwt jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("preferred_username", "testuser")
            .build();

    // This should now succeed and not log ERROR (only log WARN)
    mockMvc
        .perform(
            get("/api/v1/missions")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt)))
        .andExpect(status().isOk());
  }

  @Test
  void testAuthenticatedAccessWithBothNullSubAndUsername() throws Exception {
    // We create a JWT without sub and without preferred_username, but with some other claim to
    // satisfy Jwt.Builder
    org.springframework.security.oauth2.jwt.Jwt jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("foo", "bar")
            .build();

    // This should log ERROR but still return 200 for permitAll
    mockMvc
        .perform(
            get("/api/v1/missions")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt)))
        .andExpect(status().isOk());
  }
}
