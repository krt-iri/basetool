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

package de.greluc.krt.iri.basetool.ingest.service;

import de.greluc.krt.iri.basetool.ingest.config.IngestProperties;
import de.greluc.krt.iri.basetool.ingest.model.dto.HandoffKind;
import de.greluc.krt.iri.basetool.ingest.model.dto.StagedHandoff;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Stages and consumes the short-lived, single-use browser handoffs in Redis (REQ-INGEST-003). A
 * staged entry is keyed by {@code (sub, handoffId)}, expires after {@link
 * IngestProperties#getHandoffTtl()}, and is deleted on the first successful read — so a stolen or
 * replayed id is useless, and the entry is scoped to the user who created it.
 *
 * <p>Key schema (shared with the frontend, which performs the consuming read after login): {@code
 * ingest:handoff:&lt;sub&gt;:&lt;handoffId&gt;} → a {@link StagedHandoff} JSON document. No
 * screenshots and no raw image bytes are ever staged — only the already-matched draft.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandoffStagingService {

  /** Redis key prefix; the full key is {@code ingest:handoff:<sub>:<handoffId>}. */
  public static final String KEY_PREFIX = "ingest:handoff:";

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final IngestProperties ingestProperties;

  /**
   * Stages a draft for one-time pickup and returns a fresh, unguessable handoff id. The id is 160
   * bits of {@link SecureRandom} entropy (URL-safe base64), comfortably above the 128-bit floor.
   *
   * @param sub the authenticated caller's subject; the entry is readable only under this subject
   * @param kind which draft is being staged
   * @param draftJson the backend draft response, stored verbatim
   * @return the generated handoff id
   */
  public @NotNull String stage(
      @NotNull String sub, @NotNull HandoffKind kind, @NotNull String draftJson) {
    byte[] raw = new byte[20];
    RANDOM.nextBytes(raw);
    String handoffId = URL_ENCODER.encodeToString(raw);
    String value = objectMapper.writeValueAsString(new StagedHandoff(kind, draftJson));
    redisTemplate.opsForValue().set(key(sub, handoffId), value, ingestProperties.getHandoffTtl());
    // Never log the draft or the id at INFO — only that a handoff of this kind was staged.
    log.debug("Staged {} handoff for caller", kind);
    return handoffId;
  }

  /**
   * Atomically reads and deletes the staged handoff for {@code (sub, handoffId)} — the single-use
   * consume. Returns empty when the id is unknown, expired, already consumed, or staged under a
   * different subject; no distinction is exposed so a probe cannot tell "wrong owner" from "never
   * existed".
   *
   * @param sub the caller's subject
   * @param handoffId the handoff id from the {@code ?handoff=} parameter
   * @return the staged handoff, or empty if there is nothing to hand off
   */
  public @NotNull Optional<StagedHandoff> consume(@NotNull String sub, @NotNull String handoffId) {
    String value = redisTemplate.opsForValue().getAndDelete(key(sub, handoffId));
    if (value == null) {
      return Optional.empty();
    }
    return Optional.of(objectMapper.readValue(value, StagedHandoff.class));
  }

  /**
   * Builds the Redis key for a {@code (sub, handoffId)} pair.
   *
   * @param sub the caller's subject
   * @param handoffId the handoff id
   * @return the namespaced Redis key
   */
  private static @NotNull String key(@NotNull String sub, @NotNull String handoffId) {
    return KEY_PREFIX + sub + ":" + handoffId;
  }
}
