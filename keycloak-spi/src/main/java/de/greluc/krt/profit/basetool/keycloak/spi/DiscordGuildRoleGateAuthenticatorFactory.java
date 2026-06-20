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

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Factory for {@link DiscordGuildRoleGateAuthenticator}.
 *
 * <p>Registered via {@code META-INF/services/org.keycloak.authentication.AuthenticatorFactory} so
 * the gate can be added as a {@code REQUIRED} execution to a custom <em>First Broker Login</em>
 * flow and bound to the Discord IdP. The config properties (guild id, KRT-Mitglied role id, API
 * base URL) are declared here so they are editable in the admin console; the T1.0 authenticator
 * stub does not read them yet — T1.2 (#723) consumes them in the real gate.
 */
public class DiscordGuildRoleGateAuthenticatorFactory implements AuthenticatorFactory {

  /** Stable provider id shown in the authentication-flow editor. */
  public static final String PROVIDER_ID = "discord-guild-role-gate";

  /** Config key — the das-kartell guild id (numeric snowflake). */
  public static final String CONFIG_GUILD_ID = "guildId";

  /** Config key — the KRT-Mitglied role id (numeric snowflake) required for entry. */
  public static final String CONFIG_KRT_MITGLIED_ROLE_ID = "krtMitgliedRoleId";

  /** Config key — the Discord API base URL (override only for tests). */
  public static final String CONFIG_API_BASE_URL = "apiBaseUrl";

  private static final String DEFAULT_API_BASE_URL = "https://discord.com/api/v10";

  private static final DiscordGuildRoleGateAuthenticator INSTANCE =
      new DiscordGuildRoleGateAuthenticator();

  private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
    AuthenticationExecutionModel.Requirement.REQUIRED,
    AuthenticationExecutionModel.Requirement.DISABLED
  };

  @Override
  public Authenticator create(KeycloakSession session) {
    return INSTANCE;
  }

  @Override
  public void init(Config.Scope config) {
    // No realm-independent bootstrap state.
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    // No cross-provider wiring needed.
  }

  @Override
  public void close() {
    // Stateless singleton; nothing to release.
  }

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public String getDisplayType() {
    return "Discord Guild + KRT-Mitglied Gate";
  }

  @Override
  public String getReferenceCategory() {
    return "Discord";
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Override
  public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
    return REQUIREMENT_CHOICES;
  }

  @Override
  public boolean isUserSetupAllowed() {
    return false;
  }

  @Override
  public String getHelpText() {
    return "Denies first-broker login unless the federated Discord user is a member of the "
        + "configured guild and holds the configured KRT-Mitglied role (matched by numeric id). "
        + "Fails closed on any error or ambiguity.";
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    ProviderConfigProperty guildId =
        new ProviderConfigProperty(
            CONFIG_GUILD_ID,
            "Guild ID",
            "The das-kartell Discord guild (server) id whose membership is required.",
            ProviderConfigProperty.STRING_TYPE,
            null);
    ProviderConfigProperty roleId =
        new ProviderConfigProperty(
            CONFIG_KRT_MITGLIED_ROLE_ID,
            "KRT-Mitglied role ID",
            "The numeric Discord role id (NOT the display name) a member must hold to be admitted.",
            ProviderConfigProperty.STRING_TYPE,
            null);
    ProviderConfigProperty apiBaseUrl =
        new ProviderConfigProperty(
            CONFIG_API_BASE_URL,
            "Discord API base URL",
            "Base URL of the Discord API. Override only for testing; defaults to the public API.",
            ProviderConfigProperty.STRING_TYPE,
            DEFAULT_API_BASE_URL);
    return List.of(guildId, roleId, apiBaseUrl);
  }
}
