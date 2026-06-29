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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Orchestration tests for {@link DiscordGuildRoleGateAuthenticator#authenticate}: the fail-closed
 * membership branches (missing config, missing brokered token, membership denial) and the fail-open
 * account-existence precheck (REQ-SEC-022) — deny-on-collision, allow-on-no-collision,
 * allow-on-uncertain (fail open), and the three skip paths (account linking, unconfigured URL,
 * non-HTTPS URL). The brokered-identity and environment reads are overridden per test (they would
 * otherwise need a live first-broker-login session and real environment variables).
 */
@ExtendWith(MockitoExtension.class)
class DiscordGuildRoleGateAuthenticatorTest {

  @Mock private AuthenticationFlowContext context;
  @Mock private DiscordMembershipChecker checker;
  @Mock private DiscordGuildNicknameReader nicknameReader;
  @Mock private BackendAccountChecker backendChecker;
  @Mock private LoginFormsProvider form;

  private static final Map<String, String> VALID_CONFIG =
      Map.of("guildId", "123", "krtMitgliedRoleId", "999");
  private static final String PRECHECK_URL =
      "https://backend:11261/internal/discord/account-existence";
  private static final String SECRET = "s3cr3t";

  // Overridable env/identity seams, settable per test before building the authenticator.
  private String precheckUrl;
  private String sharedSecret;
  private boolean accountLinking;

  /**
   * Builds the authenticator with overridden brokered-identity + environment seams, bypassing the
   * live session and process environment.
   */
  private DiscordGuildRoleGateAuthenticator authenticator(
      String token, String username, String email) {
    return new DiscordGuildRoleGateAuthenticator(checker, nicknameReader, backendChecker) {
      @Override
      Brokered brokered(AuthenticationFlowContext ctx) {
        return token == null ? null : new Brokered(token, username, email);
      }

      @Override
      boolean isAccountLinking(AuthenticationFlowContext ctx) {
        return accountLinking;
      }

      @Override
      String backendPrecheckUrl() {
        return precheckUrl;
      }

      @Override
      String backendSharedSecret() {
        return sharedSecret;
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

    authenticator("tok", "Maverick", "mav@example.com").authenticate(context);

    verify(context).failure(eq(AuthenticationFlowError.ACCESS_DENIED), any());
    verifyNoInteractions(checker, nicknameReader, backendChecker);
  }

  @Test
  void deniesClosed_whenBrokeredTokenMissing() {
    stubConfig(VALID_CONFIG);
    stubDenyForm();

    authenticator(null, null, null).authenticate(context);

    verify(context).failure(eq(AuthenticationFlowError.ACCESS_DENIED), any());
    verifyNoInteractions(checker, nicknameReader, backendChecker);
  }

  @Test
  void denies_whenMembershipCheckerDenies() {
    stubConfig(VALID_CONFIG);
    stubDenyForm();
    when(checker.check(any(), any(), any(), any()))
        .thenReturn(DiscordMembershipChecker.Result.DENIED_NOT_MEMBER);

    authenticator("tok", "Maverick", "mav@example.com").authenticate(context);

    verify(context).failure(eq(AuthenticationFlowError.ACCESS_DENIED), any());
    verify(form).setError(DiscordGuildRoleGateAuthenticator.ERROR_MESSAGE_KEY);
    verifyNoInteractions(nicknameReader, backendChecker);
  }

  @Test
  void succeeds_whenMembershipAllows_andPrecheckUnconfigured() {
    stubConfig(VALID_CONFIG);
    when(checker.check(any(), eq("123"), eq("999"), eq("tok")))
        .thenReturn(DiscordMembershipChecker.Result.ALLOWED);

    authenticator("tok", "Maverick", "mav@example.com").authenticate(context);

    verify(context).success();
    verifyNoInteractions(nicknameReader, backendChecker);
  }

  @Test
  void deniesAccountExists_whenBackendReportsExists() {
    precheckUrl = PRECHECK_URL;
    sharedSecret = SECRET;
    stubConfig(VALID_CONFIG);
    stubDenyForm();
    when(checker.check(any(), any(), any(), any()))
        .thenReturn(DiscordMembershipChecker.Result.ALLOWED);
    when(nicknameReader.readNickname(any(), any(), any())).thenReturn(Optional.of("Mav"));
    when(backendChecker.check(
            eq(PRECHECK_URL), eq(SECRET), eq("Maverick"), eq("mav@example.com"), eq("Mav")))
        .thenReturn(BackendAccountChecker.Result.EXISTS);

    authenticator("tok", "Maverick", "mav@example.com").authenticate(context);

    verify(context).failure(eq(AuthenticationFlowError.ACCESS_DENIED), any());
    verify(form).setError(DiscordGuildRoleGateAuthenticator.ACCOUNT_EXISTS_MESSAGE_KEY);
  }

  @Test
  void succeeds_whenBackendReportsNotExists() {
    precheckUrl = PRECHECK_URL;
    sharedSecret = SECRET;
    stubConfig(VALID_CONFIG);
    when(checker.check(any(), any(), any(), any()))
        .thenReturn(DiscordMembershipChecker.Result.ALLOWED);
    when(nicknameReader.readNickname(any(), any(), any())).thenReturn(Optional.empty());
    when(backendChecker.check(any(), any(), any(), any(), any()))
        .thenReturn(BackendAccountChecker.Result.NOT_EXISTS);

    authenticator("tok", "Maverick", "mav@example.com").authenticate(context);

    verify(context).success();
  }

  @Test
  void succeedsFailOpen_whenBackendUnknown() {
    precheckUrl = PRECHECK_URL;
    sharedSecret = SECRET;
    stubConfig(VALID_CONFIG);
    when(checker.check(any(), any(), any(), any()))
        .thenReturn(DiscordMembershipChecker.Result.ALLOWED);
    when(nicknameReader.readNickname(any(), any(), any())).thenReturn(Optional.empty());
    when(backendChecker.check(any(), any(), any(), any(), any()))
        .thenReturn(BackendAccountChecker.Result.UNKNOWN);

    authenticator("tok", "Maverick", "mav@example.com").authenticate(context);

    verify(context).success();
  }

  @Test
  void skipsPrecheck_whenAccountLinking() {
    precheckUrl = PRECHECK_URL;
    sharedSecret = SECRET;
    accountLinking = true;
    stubConfig(VALID_CONFIG);
    when(checker.check(any(), any(), any(), any()))
        .thenReturn(DiscordMembershipChecker.Result.ALLOWED);

    authenticator("tok", "Maverick", "mav@example.com").authenticate(context);

    verify(context).success();
    verifyNoInteractions(nicknameReader, backendChecker);
  }

  @Test
  void skipsPrecheck_whenUrlNotConfigured() {
    precheckUrl = null;
    sharedSecret = SECRET;
    stubConfig(VALID_CONFIG);
    when(checker.check(any(), any(), any(), any()))
        .thenReturn(DiscordMembershipChecker.Result.ALLOWED);

    authenticator("tok", "Maverick", "mav@example.com").authenticate(context);

    verify(context).success();
    verifyNoInteractions(nicknameReader, backendChecker);
  }

  @Test
  void skipsPrecheck_whenUrlNotHttps() {
    precheckUrl = "http://backend:11261/internal/discord/account-existence";
    sharedSecret = SECRET;
    stubConfig(VALID_CONFIG);
    when(checker.check(any(), any(), any(), any()))
        .thenReturn(DiscordMembershipChecker.Result.ALLOWED);

    authenticator("tok", "Maverick", "mav@example.com").authenticate(context);

    verify(context).success();
    verifyNoInteractions(nicknameReader, backendChecker);
  }
}
