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
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.keycloak.util.JsonSerialization;

/**
 * Best-effort reader for a Discord user's per-guild server nickname — the {@code nick} field of the
 * guild-member object returned by {@code GET {apiBaseUrl}/users/@me/guilds/{guildId}/member}.
 * Called with the user's own brokered access token (scope {@code guilds.members.read}) so no bot is
 * needed, the same source the membership gate uses (REQ-DATA-008).
 *
 * <p><strong>Fails open.</strong> Unlike {@link DiscordMembershipChecker} (which gates the login
 * and fails <em>closed</em>), the nickname is purely cosmetic — it is shown to an admin at approval
 * time only. Any outcome other than an HTTP 200 carrying a non-blank {@code nick} (a non-200
 * status, an absent/null/blank nickname, a malformed body, a network error, or a timeout) yields
 * {@link Optional#empty()} and never throws. Capturing the nickname therefore cannot break the
 * login and cannot delay it beyond the bounded request timeout; the caller treats an empty result
 * as "no nickname captured".
 *
 * <p>This class never logs the token, the response body, or the nickname.
 */
public class DiscordGuildNicknameReader {

  /**
   * Defensive upper bound on the captured nickname length. Discord caps a server nickname at 32
   * characters, so this only guards against a hostile or malformed body and keeps the value well
   * within the backend column width.
   */
  private static final int MAX_NICK_LENGTH = 100;

  private final HttpClient httpClient;
  private final Duration requestTimeout;

  /**
   * Creates a reader.
   *
   * @param httpClient the HTTP client used for the Discord call
   * @param requestTimeout per-request timeout; exceeding it yields an empty result (fail open)
   */
  public DiscordGuildNicknameReader(HttpClient httpClient, Duration requestTimeout) {
    this.httpClient = httpClient;
    this.requestTimeout = requestTimeout;
  }

  /**
   * Reads the user's server nickname in the given guild, best-effort.
   *
   * @param apiBaseUrl Discord API base URL, e.g. {@code https://discord.com/api/v10}
   * @param guildId the guild (server) id whose nickname is wanted
   * @param accessToken the user's brokered Discord access token (scope {@code guilds.members.read})
   * @return the trimmed, length-bounded nickname, or {@link Optional#empty()} when absent or on any
   *     error
   */
  public Optional<String> readNickname(String apiBaseUrl, String guildId, String accessToken) {
    String url = apiBaseUrl + "/users/@me/guilds/" + guildId + "/member";
    HttpResponse<String> response;
    try {
      response =
          httpClient.send(buildRequest(url, accessToken), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      // Timeout / connection reset / DNS failure / truncated read — fail open (no nickname).
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
    if (response.statusCode() != 200) {
      return Optional.empty();
    }
    return extractNick(response.body());
  }

  private HttpRequest buildRequest(String url, String accessToken) {
    return HttpRequest.newBuilder(URI.create(url))
        .timeout(requestTimeout)
        .header("Authorization", "Bearer " + accessToken)
        .header("Accept", "application/json")
        .GET()
        .build();
  }

  /**
   * Extracts and normalises the {@code nick} field from a guild-member JSON body.
   *
   * @param body the raw guild-member response body
   * @return the trimmed nickname (at most {@value #MAX_NICK_LENGTH} characters), or {@link
   *     Optional#empty()} when the field is absent, null, blank, or the body is unparseable
   */
  static Optional<String> extractNick(String body) {
    JsonNode member;
    try {
      member = JsonSerialization.readValue(body, JsonNode.class);
    } catch (IOException e) {
      return Optional.empty();
    }
    if (member == null) {
      return Optional.empty();
    }
    JsonNode nick = member.get("nick");
    if (nick == null || nick.isNull()) {
      return Optional.empty();
    }
    String value = nick.asText().trim();
    if (value.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        value.length() > MAX_NICK_LENGTH ? value.substring(0, MAX_NICK_LENGTH) : value);
  }
}
