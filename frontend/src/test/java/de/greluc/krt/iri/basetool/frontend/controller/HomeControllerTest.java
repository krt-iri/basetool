package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

class HomeControllerTest {

  @Test
  void home_ShouldUsePreferredUsername_InsteadOfFullName() {
    // Arrange
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    HomeController controller = new HomeController(backendApiClient);
    Model model = new ConcurrentModel();
    HttpSession session = mock(HttpSession.class);
    OidcUser user = mock(OidcUser.class);

    // Setup User
    when(user.getFullName()).thenReturn("Max Mustermann");
    when(user.getPreferredUsername()).thenReturn("max_muster");
    // Mock Authorities for admin check
    doReturn(Collections.emptyList()).when(user).getAuthorities();

    // Act
    String view = controller.home(model, user, session);

    // Assert
    assertEquals("index", view);
    // This assertion expects the CHANGE to be made. Currently it would fail (expecting "Max
    // Mustermann").
    // I will assert "max_muster" to verify my fix later.
    assertEquals("max_muster", model.getAttribute("username"));
  }
}
