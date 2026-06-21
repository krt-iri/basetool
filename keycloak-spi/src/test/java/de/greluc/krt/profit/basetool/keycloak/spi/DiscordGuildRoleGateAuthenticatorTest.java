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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Orchestration tests for {@link DiscordGuildRoleGateAuthenticator#authenticate}: the deny-closed
 * branches (missing config, missing brokered token) and the allow/deny dispatch on the {@link
 * DiscordMembershipChecker} result. The brokered-token extraction is overridden per test (it would
 * otherwise need a live first-broker-login session).
 */
@ExtendWith(MockitoExtension.class)
class DiscordGuildRoleGateAuthenticatorTest {

  @Mock private AuthenticationFlowContext context;
  @Mock private DiscordMembershipChecker checker;
  @Mock private LoginFormsProvider form;

  private static final Map<String, String> VALID_CONFIG =
      Map.of("guildId", "123", "krtMitgliedRoleId", "999");

  /**
   * Builds the authenticator with a fixed brokered token, bypassing the live-session extraction.
   */
  private DiscordGuildRoleGateAuthenticator authenticatorWithToken(String token) {
    return new DiscordGuildRoleGateAuthenticator(checker) {
      @Override
      String federatedAccessToken(AuthenticationFlowContext ctx) {
        return token;
      }
    };
  }

  private void stubConfig(Map<String, String> config) {
    AuthenticatorConfigModel model = mock(AuthenticatorConfigModel.class);
    when(model.getConfig()).thenReturn(config);
    when(context.getAuthenticatorConfig()).thenReturn(model);
  }

  private void stubDenyForm() {
    when(context.form()).thenReturn(form);
    when(form.setError(anyString())).thenReturn(form);
    when(form.createErrorPage(any())).thenReturn(mock(Response.class));
  }

  @Test
  void deniesClosed_whenGuildOrRoleConfigMissing() {
    when(context.getAuthenticatorConfig()).thenReturn(null);
    stubDenyForm();

    authenticatorWithToken("tok").authenticate(context);

    verify(context).failure(eq(AuthenticationFlowError.ACCESS_DENIED), any());
    verifyNoInteractions(checker);
  }

  @Test
  void deniesClosed_whenBrokeredTokenMissing() {
    stubConfig(VALID_CONFIG);
    stubDenyForm();

    authenticatorWithToken(null).authenticate(context);

    verify(context).failure(eq(AuthenticationFlowError.ACCESS_DENIED), any());
    verifyNoInteractions(checker);
  }

  @Test
  void succeeds_whenCheckerAllows() {
    stubConfig(VALID_CONFIG);
    when(checker.check(any(), eq("123"), eq("999"), eq("tok")))
        .thenReturn(DiscordMembershipChecker.Result.ALLOWED);

    authenticatorWithToken("tok").authenticate(context);

    verify(context).success();
  }

  @Test
  void denies_whenCheckerDenies() {
    stubConfig(VALID_CONFIG);
    stubDenyForm();
    when(checker.check(any(), any(), any(), any()))
        .thenReturn(DiscordMembershipChecker.Result.DENIED_NOT_MEMBER);

    authenticatorWithToken("tok").authenticate(context);

    verify(context).failure(eq(AuthenticationFlowError.ACCESS_DENIED), any());
  }
}
