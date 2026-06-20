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

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * First-broker-login authenticator that gates Discord federation on das-kartell guild membership.
 *
 * <p><strong>T1.0 scaffold — this is a deliberate no-op that allows every login.</strong> The real,
 * fail-closed gate (admit only when the federated Discord user is in the configured guild
 * <em>and</em> holds the configured KRT-Mitglied role, by numeric role id) lands in T1.2 (#723),
 * where {@link #authenticate(AuthenticationFlowContext)} will call {@code
 * /users/@me/guilds/{guild}/ member} with the brokered user token and {@code context.failure(...)}
 * on any miss or ambiguity.
 *
 * <p>Even while this stub allows federation, a brand-new user is granted no access: the backend
 * lands them in {@code PENDING} until an admin approves (T1.3). Keeping the stub no-op here lets
 * the login button (T1.1) and the {@code discord_user_id} auto-link be exercised before the gate
 * exists.
 */
public class DiscordGuildRoleGateAuthenticator implements Authenticator {

  @Override
  public void authenticate(AuthenticationFlowContext context) {
    // T1.0: no-op allow. Real fail-closed guild + KRT-Mitglied check arrives in T1.2 (#723).
    context.success();
  }

  @Override
  public void action(AuthenticationFlowContext context) {
    context.success();
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
