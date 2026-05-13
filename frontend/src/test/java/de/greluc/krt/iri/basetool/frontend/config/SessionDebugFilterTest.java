package de.greluc.krt.iri.basetool.frontend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionDebugFilterTest {

  private SessionDebugFilter filter;
  private FilterChain filterChain;

  @BeforeEach
  void setUp() {
    filter = new SessionDebugFilter();
    filterChain = mock(FilterChain.class);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // -------------------------------------------------------------------------
  // Filter chain delegation
  // -------------------------------------------------------------------------

  @Test
  void shouldAlwaysDelegateToFilterChain() throws ServletException, IOException {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    verify(filterChain, times(1)).doFilter(request, response);
  }

  @Test
  void shouldDelegateToFilterChainWhenSessionExists() throws ServletException, IOException {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/order/");
    request.getSession(true); // create session
    MockHttpServletResponse response = new MockHttpServletResponse();

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    verify(filterChain, times(1)).doFilter(request, response);
  }

  @Test
  void shouldDelegateToFilterChainWhenAuthenticated() throws ServletException, IOException {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/missions/");
    request.getSession(true);
    MockHttpServletResponse response = new MockHttpServletResponse();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("testuser", null, java.util.List.of()));

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    verify(filterChain, times(1)).doFilter(request, response);
  }

  @Test
  void shouldDelegateToFilterChainForPostRequest() throws ServletException, IOException {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/missions/create");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    verify(filterChain, times(1)).doFilter(request, response);
  }

  // -------------------------------------------------------------------------
  // Response integrity – filter must not modify response status
  // -------------------------------------------------------------------------

  @Test
  void shouldNotModifyResponseStatus() throws ServletException, IOException {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    assertEquals(200, response.getStatus());
  }

  @Test
  void shouldNotModifyResponseStatusWhenSessionExists() throws ServletException, IOException {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hangar/");
    request.getSession(true);
    MockHttpServletResponse response = new MockHttpServletResponse();

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    assertEquals(200, response.getStatus());
  }

  // -------------------------------------------------------------------------
  // SecurityContext must not be modified by the filter
  // -------------------------------------------------------------------------

  @Test
  void shouldNotModifySecurityContext() throws ServletException, IOException {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/profile/");
    MockHttpServletResponse response = new MockHttpServletResponse();
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken("user42", null, java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    assertEquals(auth, SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void shouldNotSetAuthenticationWhenNonePresent() throws ServletException, IOException {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
    MockHttpServletResponse response = new MockHttpServletResponse();
    // No authentication set

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    org.springframework.security.core.Authentication authAfter =
        SecurityContextHolder.getContext().getAuthentication();
    // Filter must not inject any authentication
    assert authAfter == null || !authAfter.isAuthenticated();
  }

  // -------------------------------------------------------------------------
  // Various HTTP methods – filter must pass all through
  // -------------------------------------------------------------------------

  @Test
  void shouldDelegateForDeleteRequest() throws ServletException, IOException {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/resource/1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    verify(filterChain, times(1)).doFilter(request, response);
  }

  @Test
  void shouldDelegateForPutRequest() throws ServletException, IOException {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/resource/1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // When
    filter.doFilter(request, response, filterChain);

    // Then
    verify(filterChain, times(1)).doFilter(request, response);
  }
}
