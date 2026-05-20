package de.greluc.krt.iri.basetool.frontend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
class SecurityHeadersTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @MockitoBean private WebClient webClient;

  @MockitoBean(name = "publicWebClient")
  private WebClient publicWebClient;

  @MockitoBean private BackendApiClient backendApiClient;

  @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void shouldExposeSecurityHeadersOnHome() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(header().exists("Content-Security-Policy"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
        .andExpect(header().exists("Permissions-Policy"));
  }

  /**
   * Audit finding L-3 (2026-05-20): the CSP {@code style-src} directive must be nonce-gated and
   * must NOT carry {@code 'unsafe-inline'} — every {@code <style>} block in the templates now
   * renders with {@code th:attr="nonce=${cspNonce}"}, so an injected {@code <style>} tag (stored
   * XSS via mission name / finance note / …) cannot be evaluated by the browser. The {@code
   * style=""} attributes on individual elements remain allowed via the explicit {@code
   * style-src-attr 'unsafe-inline'} fallback — that is a separate CSP3 directive and is pinned here
   * so future tightening (e.g. moving to {@code 'unsafe-hashes' 'sha256-…'}) can land on
   * style-src-attr without touching style-src.
   */
  @Test
  void cspStyleSrcIsNonceGated_andStyleSrcAttrIsExplicit() throws Exception {
    String csp =
        mockMvc
            .perform(get("/"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("Content-Security-Policy");

    assertThat(csp).as("Content-Security-Policy header").isNotNull();
    // Nonce embedded in style-src — the placeholder is replaced per request, so we only assert
    // the prefix + the nonce-pattern.
    assertThat(csp)
        .as("style-src must be nonce-gated, not 'unsafe-inline'")
        .containsPattern("style-src 'self' 'nonce-[A-Za-z0-9_-]+'");
    assertThat(csp)
        .as("style-src must NOT carry 'unsafe-inline' (would defeat the nonce gate)")
        .doesNotContain("style-src 'self' 'unsafe-inline'");
    // style-src-attr stays unsafe-inline for now — pinned explicitly so a future tightening can
    // move from 'unsafe-inline' to an 'unsafe-hashes' allow-list incrementally.
    assertThat(csp)
        .as("style-src-attr fallback for 859 style='…' attributes")
        .contains("style-src-attr 'unsafe-inline'");
    // script-src stays nonce-gated as before — regression-pin.
    assertThat(csp).contains("script-src 'nonce-").contains("'strict-dynamic'");
  }
}
