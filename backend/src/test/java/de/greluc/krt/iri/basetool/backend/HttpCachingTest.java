package de.greluc.krt.iri.basetool.backend;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@SpringBootTest
@ActiveProfiles("test")
class HttpCachingTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Autowired
  private de.greluc.krt.iri.basetool.backend.filter.ApiCacheControlFilter apiCacheControlFilter;

  @Autowired private ShallowEtagHeaderFilter shallowEtagHeaderFilter;

  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setup() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .addFilters(shallowEtagHeaderFilter, apiCacheControlFilter)
            .apply(springSecurity())
            .build();
  }

  @Test
  void etagAndConditionalGet_ShouldReturn304_OnMatch() throws Exception {
    String etag =
        mockMvc
            .perform(get("/api/v1/job-types"))
            .andExpect(status().isOk())
            .andExpect(
                header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")))
            .andExpect(header().string("ETag", notNullValue()))
            .andReturn()
            .getResponse()
            .getHeader("ETag");

    // Conditional GET with If-None-Match
    mockMvc
        .perform(get("/api/v1/job-types").header("If-None-Match", etag))
        .andExpect(status().isNotModified());
  }

  @Test
  void protectedEndpoint_unauthenticatedConditionalGet_isClientErrorNot304() throws Exception {
    // L-9: the ShallowEtagHeaderFilter sits at the front of the chain, but it computes the 304 from
    // the *generated* body. For an unauthenticated caller on a protected endpoint that body is
    // Spring Security's 401, not the resource — so a fabricated If-None-Match can never short-
    // circuit to a 304 of protected content. is4xxClientError() asserts the denial (401/403) and,
    // by construction, excludes the 304 (3xx) that an ETag oracle would need.
    mockMvc
        .perform(get("/api/v1/users").header("If-None-Match", "\"fabricated-etag\""))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void protectedEndpoint_etagReplayedWithoutAuth_isDeniedNot304() throws Exception {
    SimpleGrantedAuthority member = new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER");

    // An authorized member reads the protected endpoint and obtains its real ETag...
    String etag =
        mockMvc
            .perform(get("/api/v1/users").with(jwt().authorities(member)))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", notNullValue()))
            .andReturn()
            .getResponse()
            .getHeader("ETag");
    assertNotNull(etag);

    // ...the same authorized principal replaying that ETag gets the intended 304 (caching works)...
    mockMvc
        .perform(get("/api/v1/users").with(jwt().authorities(member)).header("If-None-Match", etag))
        .andExpect(status().isNotModified());

    // ...but replaying the very same ETag WITHOUT authentication must NOT yield a 304 (L-9): there
    // is no cross-principal ETag equality oracle — security denies before the comparison happens.
    mockMvc
        .perform(get("/api/v1/users").header("If-None-Match", etag))
        .andExpect(status().is4xxClientError());
  }
}
