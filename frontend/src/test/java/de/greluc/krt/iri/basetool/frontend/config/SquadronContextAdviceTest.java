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
 * Unit tests for the {@code canSeeBlueprintOverview} sidebar gate in {@link SquadronContextAdvice}
 * (#364): admins are allowed without a backend round-trip, non-admins defer to the backend
 * capability, and any failure fails closed (menu hidden).
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
  void canSeeBlueprintOverview_anonymous_isFalse_withoutBackendCall() {
    when(authHelper.isAuthenticated()).thenReturn(false);

    assertFalse(advice().canSeeBlueprintOverview());
    verify(backendApiClient, never()).get(any(String.class), any(Class.class));
  }

  @Test
  void canSeeBlueprintOverview_admin_isTrue_withoutBackendCall() {
    when(authHelper.isAuthenticated()).thenReturn(true);
    when(authHelper.isAdmin()).thenReturn(true);

    assertTrue(advice().canSeeBlueprintOverview());
    verify(backendApiClient, never()).get(any(String.class), any(Class.class));
  }

  @Test
  void canSeeBlueprintOverview_nonAdmin_reflectsBackendCapability() {
    when(authHelper.isAuthenticated()).thenReturn(true);
    when(authHelper.isAdmin()).thenReturn(false);
    when(backendApiClient.get("/api/v1/me/capabilities", CapabilitiesResponse.class))
        .thenReturn(new CapabilitiesResponse(true));

    assertTrue(advice().canSeeBlueprintOverview());
  }

  @Test
  void canSeeBlueprintOverview_nonAdmin_backendFails_failsClosed() {
    when(authHelper.isAuthenticated()).thenReturn(true);
    when(authHelper.isAdmin()).thenReturn(false);
    when(backendApiClient.get("/api/v1/me/capabilities", CapabilitiesResponse.class))
        .thenThrow(new RuntimeException("boom"));

    assertFalse(advice().canSeeBlueprintOverview());
  }
}
