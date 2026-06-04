/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.iri.basetool.frontend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

/**
 * Smart OIDC logout handler that avoids sending a logout request to Keycloak when the user's OIDC
 * session has already expired.
 *
 * <p>If the {@link Authentication} contains a valid {@link OidcUser} with an ID token, the standard
 * {@link OidcClientInitiatedLogoutSuccessHandler} is used to properly end the Keycloak session via
 * the OIDC end-session endpoint. This is the normal path.
 *
 * <p>If the authentication is missing, not an OIDC token, or the ID token is absent (i.e. the
 * Keycloak session has already expired), the handler skips the Keycloak endpoint call entirely and
 * redirects directly to the post-logout URL (login page). This prevents the recurring {@code
 * LOGOUT_ERROR session_expired} warnings in Keycloak.
 */
@Slf4j
public class SmartOidcLogoutSuccessHandler extends SimpleUrlLogoutSuccessHandler {

  private final OidcClientInitiatedLogoutSuccessHandler oidcHandler;

  /**
   * Performs smart oidc logout success handler.
   *
   * @param clientRegistrationRepository Keycloak client registry for resolving the end-session
   *     endpoint URL
   * @param postLogoutRedirectUri target URL to redirect to after a successful logout (may contain
   *     the {@code {baseUrl}} placeholder)
   */
  public SmartOidcLogoutSuccessHandler(
      @NotNull ClientRegistrationRepository clientRegistrationRepository,
      @NotNull String postLogoutRedirectUri) {
    this.oidcHandler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
    this.oidcHandler.setPostLogoutRedirectUri(postLogoutRedirectUri);
    setDefaultTargetUrl("/");
  }

  @Override
  public void onLogoutSuccess(
      @NotNull HttpServletRequest request,
      @NotNull HttpServletResponse response,
      Authentication authentication)
      throws IOException, jakarta.servlet.ServletException {
    if (hasValidOidcToken(authentication)) {
      log.debug(
          "[Logout] Active OIDC session found – delegating to Keycloak end-session endpoint.");
      oidcHandler.onLogoutSuccess(request, response, authentication);
    } else {
      log.info(
          "[Logout] No active OIDC ID token found (session already expired) – skipping Keycloak"
              + " logout endpoint, redirecting directly to login page.");
      super.onLogoutSuccess(request, response, authentication);
    }
  }

  private boolean hasValidOidcToken(Authentication authentication) {
    if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
      return false;
    }
    if (!(oauthToken.getPrincipal() instanceof OidcUser oidcUser)) {
      return false;
    }
    return oidcUser.getIdToken() != null;
  }
}
