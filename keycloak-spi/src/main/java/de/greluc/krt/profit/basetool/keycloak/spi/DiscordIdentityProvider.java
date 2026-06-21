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

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider;
import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig;
import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;
import org.keycloak.broker.provider.AuthenticationRequest;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.util.JsonSerialization;

/**
 * Discord identity provider that brokers a Discord login into Keycloak.
 *
 * <p>Discord is a plain OAuth 2.0 provider — it issues no OIDC {@code id_token} — so this extends
 * {@link AbstractOAuth2IdentityProvider} (the same base Keycloak's own GitHub/Google social
 * providers use) rather than the OIDC provider. After the authorization-code exchange it reads the
 * Discord profile from {@code GET /users/@me} with the obtained access token and maps it into a
 * {@link BrokeredIdentityContext}. The raw profile JSON is stashed for {@link
 * DiscordUserAttributeMapper}, which lets an admin import the Discord user id into the {@code
 * discord_user_id} user attribute (the auto-link, epic #720 / REQ-DATA-006).
 *
 * <p>The default scopes include {@code guilds.members.read}; the first-login membership gate
 * ({@link DiscordGuildRoleGateAuthenticator}, T1.2) reuses the stored access token to verify guild
 * + role membership. This provider only federates the identity — it grants no access on its own.
 */
public class DiscordIdentityProvider
    extends AbstractOAuth2IdentityProvider<OAuth2IdentityProviderConfig>
    implements SocialIdentityProvider<OAuth2IdentityProviderConfig> {

  /** Discord OAuth2 authorization endpoint. */
  public static final String AUTH_URL = "https://discord.com/api/oauth2/authorize";

  /** Discord OAuth2 token endpoint. */
  public static final String TOKEN_URL = "https://discord.com/api/oauth2/token";

  /** Discord current-user profile endpoint ({@code GET /users/@me}). */
  public static final String PROFILE_URL = "https://discord.com/api/v10/users/@me";

  /**
   * Default OAuth2 scopes. {@code identify} + {@code email} populate the brokered profile; {@code
   * guilds.members.read} is required by the membership gate (T1.2) to read the user's roles in the
   * configured guild via the user's own token.
   */
  public static final String DEFAULT_SCOPE = "identify email guilds.members.read";

  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();

  /**
   * Creates the provider and pins the Discord OAuth2 endpoints onto its config.
   *
   * @param session the current Keycloak session
   * @param config the brokered identity-provider config (endpoints are set here)
   */
  public DiscordIdentityProvider(KeycloakSession session, OAuth2IdentityProviderConfig config) {
    super(session, config);
    config.setAuthorizationUrl(AUTH_URL);
    config.setTokenUrl(TOKEN_URL);
    config.setUserInfoUrl(PROFILE_URL);
  }

  @Override
  protected String getDefaultScopes() {
    return DEFAULT_SCOPE;
  }

  /**
   * Appends {@code prompt=none} to Discord's authorization request so a returning member is not
   * shown the OAuth consent screen on every login. Discord defaults to {@code prompt=consent},
   * which re-prompts on each authorization; with {@code prompt=none} Discord skips the screen once
   * the user has authorized the app for these scopes — the very first authorization still shows it.
   * This only changes the consent UX: the full authorization-code exchange (and therefore the
   * membership gate, which reuses the obtained access token) is unaffected.
   *
   * @param request the brokered authentication request being built
   * @return the authorization-URL builder with {@code prompt=none} appended
   */
  @Override
  protected UriBuilder createAuthorizationUrl(AuthenticationRequest request) {
    return super.createAuthorizationUrl(request).queryParam("prompt", "none");
  }

  @Override
  protected BrokeredIdentityContext doGetFederatedIdentity(String accessToken) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(PROFILE_URL))
            .timeout(HTTP_TIMEOUT)
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/json")
            .GET()
            .build();
    try {
      HttpResponse<String> response =
          HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        // Status code only — never the body, which carries the Discord identity.
        throw new IdentityBrokerException(
            "Discord profile request returned HTTP " + response.statusCode());
      }
      JsonNode profile = JsonSerialization.readValue(response.body(), JsonNode.class);
      return extractIdentityFromProfile(null, profile);
    } catch (IOException e) {
      throw new IdentityBrokerException("Could not obtain the Discord user profile", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IdentityBrokerException("Interrupted while obtaining the Discord user profile", e);
    }
  }

  /**
   * Builds the brokered identity from the Discord {@code /users/@me} profile and stores the raw
   * profile JSON so {@link DiscordUserAttributeMapper} can import fields (e.g. {@code id}) into
   * user attributes. Never logs the profile, the id, the e-mail or any token.
   *
   * @param event the broker event builder (unused; the Discord flow has no extra events)
   * @param profile the parsed {@code /users/@me} response
   * @return the brokered identity keyed by the Discord user id (snowflake)
   */
  @Override
  protected BrokeredIdentityContext extractIdentityFromProfile(
      EventBuilder event, JsonNode profile) {
    String id = getJsonProperty(profile, "id");
    String username = getJsonProperty(profile, "username");
    String email = getJsonProperty(profile, "email");

    BrokeredIdentityContext user = new BrokeredIdentityContext(id, getConfig());
    user.setUsername(username != null ? username : id);
    if (email != null) {
      user.setEmail(email);
    }
    user.setIdp(this);

    AbstractJsonUserAttributeMapper.storeUserProfileForMapper(
        user, profile, getConfig().getAlias());
    return user;
  }
}
