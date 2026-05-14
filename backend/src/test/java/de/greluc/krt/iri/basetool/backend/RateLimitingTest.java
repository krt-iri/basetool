package de.greluc.krt.iri.basetool.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "app.rate-limit.enabled=true",
      "app.rate-limit.paths=/**",
      "app.rate-limit.capacity=2",
      "app.rate-limit.refillTokens=2",
      "app.rate-limit.refillPeriod=1h"
    })
class RateLimitingTest {

  @Autowired private WebApplicationContext context;

  @Autowired
  private de.greluc.krt.iri.basetool.backend.filter.RateLimitingFilter rateLimitingFilter;

  private MockMvc mockMvc;

  @BeforeEach
  void setup() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .addFilters(rateLimitingFilter)
            .build();
  }

  @Test
  void adminRoles_ShouldBeRateLimited_AfterQuota() throws Exception {
    // First request (allowed)
    mockMvc
        .perform(
            get("/api/v1/admin/roles")
                .with(
                    jwt()
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_ADMIN"),
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_MANAGE"),
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "USER_MANAGE"),
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "MISSION_MANAGE")))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Rate-Limit-Limit", "2"));

    // Second request (allowed, remaining 0)
    mockMvc
        .perform(
            get("/api/v1/admin/roles")
                .with(
                    jwt()
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_ADMIN"),
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_MANAGE"),
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "USER_MANAGE"),
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "MISSION_MANAGE")))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Rate-Limit-Limit", "2"))
        .andExpect(header().string("X-Rate-Limit-Remaining", "0"));

    // Third request (should be 429)
    mockMvc
        .perform(
            get("/api/v1/admin/roles")
                .with(
                    jwt()
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_ADMIN"),
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_MANAGE"),
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "USER_MANAGE"),
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "MISSION_MANAGE")))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isTooManyRequests())
        .andExpect(
            header()
                .string("X-Rate-Limit-Retry-After-Seconds", org.hamcrest.Matchers.notNullValue()))
        .andExpect(content().contentType("application/problem+json"));
  }
}
