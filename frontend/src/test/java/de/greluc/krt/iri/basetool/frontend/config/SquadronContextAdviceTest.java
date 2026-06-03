package de.greluc.krt.iri.basetool.frontend.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.frontend.config.SquadronContextAdvice.CapabilitiesResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.FrontendAuthHelperService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

/**
 * Unit tests for the per-principal capability gates in {@link SquadronContextAdvice}: the shared
 * {@code meCapabilities} resolver (a single backend round-trip, admins short-circuited, anonymous
 * short-circuited, fail-closed on error) and the two derived sidebar flags {@code
 * canSeeBlueprintOverview} (#364) and {@code canViewJobOrders} (profit-eligible order visibility).
 */
@ExtendWith(MockitoExtension.class)
class SquadronContextAdviceTest {

  @Mock private BackendApiClient backendApiClient;
  @Mock private MessageSource messageSource;
  @Mock private FrontendAuthHelperService authHelper;

  private SquadronContextAdvice advice() {
    return new SquadronContextAdvice(backendApiClient, messageSource, authHelper);
  }

  @Test
  void meCapabilities_anonymous_allFalse_withoutBackendCall() {
    when(authHelper.isAuthenticated()).thenReturn(false);

    CapabilitiesResponse caps = advice().meCapabilities();

    assertFalse(caps.canSeeBlueprintOverview());
    assertFalse(caps.canViewJobOrders());
    verify(backendApiClient, never()).get(any(String.class), any(Class.class));
  }

  @Test
  void meCapabilities_admin_allTrue_withoutBackendCall() {
    when(authHelper.isAuthenticated()).thenReturn(true);
    when(authHelper.isAdmin()).thenReturn(true);

    CapabilitiesResponse caps = advice().meCapabilities();

    assertTrue(caps.canSeeBlueprintOverview());
    assertTrue(caps.canViewJobOrders());
    verify(backendApiClient, never()).get(any(String.class), any(Class.class));
  }

  @Test
  void meCapabilities_nonAdmin_reflectsBackend() {
    when(authHelper.isAuthenticated()).thenReturn(true);
    when(authHelper.isAdmin()).thenReturn(false);
    when(backendApiClient.get("/api/v1/me/capabilities", CapabilitiesResponse.class))
        .thenReturn(new CapabilitiesResponse(true, false));

    CapabilitiesResponse caps = advice().meCapabilities();

    assertTrue(caps.canSeeBlueprintOverview());
    assertFalse(caps.canViewJobOrders());
  }

  @Test
  void meCapabilities_nonAdmin_backendFails_failsClosed() {
    when(authHelper.isAuthenticated()).thenReturn(true);
    when(authHelper.isAdmin()).thenReturn(false);
    when(backendApiClient.get("/api/v1/me/capabilities", CapabilitiesResponse.class))
        .thenThrow(new RuntimeException("boom"));

    CapabilitiesResponse caps = advice().meCapabilities();

    assertFalse(caps.canSeeBlueprintOverview());
    assertFalse(caps.canViewJobOrders());
  }

  @Test
  void derivedFlags_readFromCapabilities() {
    assertTrue(advice().canSeeBlueprintOverview(new CapabilitiesResponse(true, false)));
    assertFalse(advice().canSeeBlueprintOverview(new CapabilitiesResponse(false, true)));
    assertTrue(advice().canViewJobOrders(new CapabilitiesResponse(false, true)));
    assertFalse(advice().canViewJobOrders(new CapabilitiesResponse(true, false)));
  }

  @Test
  void derivedFlags_nullCapabilities_areFalse() {
    assertFalse(advice().canSeeBlueprintOverview(null));
    assertFalse(advice().canViewJobOrders(null));
  }
}
