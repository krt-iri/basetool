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

package de.greluc.krt.profit.basetool.keycloak.spi;

import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * First-broker-login authenticator that gates Discord federation on das-kartell guild membership.
 *
 * <p>It admits the login only when the brokered Discord user is in the configured guild and holds
 * the configured KRT-Mitglied role, delegating the decision to {@link DiscordMembershipChecker}
 * (which fails closed). It reads the guild id, role id and API base URL from this authenticator's
 * per-flow config, and the user's Discord access token from the brokered identity stored on the
 * authentication session. On any denial it renders a localized error page and ends the flow with
 * {@link AuthenticationFlowError#ACCESS_DENIED}, so no Keycloak session is issued (REQ-SEC-016).
 *
 * <p>It never logs the token, the membership payload, or any Discord id — only the coarse decision.
 */
public class DiscordGuildRoleGateAuthenticator implements Authenticator {

  /**
   * Login-theme message key used for the denial page. Add a localized entry to the krt-theme login
   * messages; an absent key renders as the key itself (still a hard denial).
   */
  static final String ERROR_MESSAGE_KEY = "discordMembershipDenied";

  private static final String DEFAULT_API_BASE_URL = "https://discord.com/api/v10";
  private static final Logger LOG = Logger.getLogger(DiscordGuildRoleGateAuthenticator.class);

  private final DiscordMembershipChecker checker;

  /**
   * Creates the authenticator.
   *
   * @param checker the fail-closed membership decision logic
   */
  public DiscordGuildRoleGateAuthenticator(DiscordMembershipChecker checker) {
    this.checker = checker;
  }

  @Override
  public void authenticate(AuthenticationFlowContext context) {
    Map<String, String> config = config(context);
    String guildId =
        trimToNull(config.get(DiscordGuildRoleGateAuthenticatorFactory.CONFIG_GUILD_ID));
    String roleId =
        trimToNull(
            config.get(DiscordGuildRoleGateAuthenticatorFactory.CONFIG_KRT_MITGLIED_ROLE_ID));
    String apiBaseUrl =
        orDefault(
            config.get(DiscordGuildRoleGateAuthenticatorFactory.CONFIG_API_BASE_URL),
            DEFAULT_API_BASE_URL);

    if (guildId == null || roleId == null) {
      LOG.error(
          "Discord guild/role gate is misconfigured (missing guildId/roleId); failing closed.");
      deny(context);
      return;
    }

    String accessToken = federatedAccessToken(context);
    if (accessToken == null) {
      LOG.warn("No federated Discord access token on the auth session; failing closed.");
      deny(context);
      return;
    }

    DiscordMembershipChecker.Result result =
        checker.check(apiBaseUrl, guildId, roleId, accessToken);
    if (result == DiscordMembershipChecker.Result.ALLOWED) {
      context.success();
    } else {
      // Coarse reason only — never the token, payload or any Discord id.
      LOG.infof("Discord membership gate denied login (reason=%s).", result);
      deny(context);
    }
  }

  private void deny(AuthenticationFlowContext context) {
    Response challenge =
        context.form().setError(ERROR_MESSAGE_KEY).createErrorPage(Response.Status.FORBIDDEN);
    context.failure(AuthenticationFlowError.ACCESS_DENIED, challenge);
  }

  private Map<String, String> config(AuthenticationFlowContext context) {
    AuthenticatorConfigModel model = context.getAuthenticatorConfig();
    return (model != null && model.getConfig() != null) ? model.getConfig() : Map.of();
  }

  /**
   * Extracts the user's brokered Discord access token from the first-broker-login session. Package-
   * visible (not private) purely so a unit test can override it without a live Keycloak session.
   *
   * @param context the authentication flow context
   * @return the brokered Discord access token, or {@code null} when none is present
   */
  String federatedAccessToken(AuthenticationFlowContext context) {
    SerializedBrokeredIdentityContext serialized =
        SerializedBrokeredIdentityContext.readFromAuthenticationSession(
            context.getAuthenticationSession(), AbstractIdpAuthenticator.BROKERED_CONTEXT_NOTE);
    if (serialized == null) {
      return null;
    }
    BrokeredIdentityContext broker =
        serialized.deserialize(context.getSession(), context.getAuthenticationSession());
    Object token =
        broker.getContextData().get(AbstractOAuth2IdentityProvider.FEDERATED_ACCESS_TOKEN);
    return token == null ? null : token.toString();
  }

  private static String trimToNull(String value) {
    return (value == null || value.isBlank()) ? null : value.trim();
  }

  private static String orDefault(String value, String fallback) {
    String trimmed = trimToNull(value);
    return trimmed == null ? fallback : trimmed;
  }

  @Override
  public void action(AuthenticationFlowContext context) {
    // Non-interactive: authenticate() terminates with success/failure, so this is never reached.
  }

  @Override
  public boolean requiresUser() {
    // The gate inspects the brokered Discord identity, not a local Keycloak user (which may not yet
    // exist during first-broker login), so no pre-existing user is required.
    return false;
  }

  @Override
  public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
    return true;
  }

  @Override
  public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    // The gate neither sets nor clears required actions.
  }

  @Override
  public void close() {
    // Stateless; nothing to release.
  }
}
