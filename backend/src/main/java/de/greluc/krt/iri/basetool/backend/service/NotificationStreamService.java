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

package de.greluc.krt.iri.basetool.backend.service;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory registry of live Server-Sent-Event subscribers, keyed by recipient {@code sub}
 * (REQ-NOTIF-010).
 *
 * <p>Single-backend-instance only — multi-instance fan-out via Redis pub/sub is a noted follow-up
 * (ADR-0016). Push is strictly best-effort: a failed send simply drops that emitter, and the
 * frontend's polling (REQ-NOTIF-006) remains the guaranteed fallback. A periodic heartbeat comment
 * keeps idle connections alive across proxies.
 */
@Service
@Slf4j
public class NotificationStreamService {

  /** How long a single SSE connection is held open before the client must reconnect. */
  private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

  private final Map<UUID, Set<SseEmitter>> emittersBySub = new ConcurrentHashMap<>();

  /**
   * Registers a new SSE subscription for a recipient and returns its emitter. The emitter
   * de-registers itself on completion, timeout or error.
   *
   * @param recipientSub the subscribing caller's {@code sub}
   * @return the registered emitter
   */
  @NotNull
  public SseEmitter subscribe(@NotNull UUID recipientSub) {
    SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
    emittersBySub.computeIfAbsent(recipientSub, key -> ConcurrentHashMap.newKeySet()).add(emitter);
    emitter.onCompletion(() -> remove(recipientSub, emitter));
    emitter.onTimeout(() -> remove(recipientSub, emitter));
    emitter.onError(error -> remove(recipientSub, emitter));
    try {
      emitter.send(SseEmitter.event().name("connected").data("ok"));
    } catch (IOException | RuntimeException e) {
      remove(recipientSub, emitter);
    }
    return emitter;
  }

  /**
   * Pushes a lightweight "notification" event to every live subscriber of the given recipients so
   * their client refreshes its unread state. Dead emitters are dropped.
   *
   * @param recipientSubs the recipients whose connections to notify
   */
  public void publish(@NotNull Collection<UUID> recipientSubs) {
    for (UUID recipientSub : recipientSubs) {
      Set<SseEmitter> emitters = emittersBySub.get(recipientSub);
      if (emitters == null) {
        continue;
      }
      for (SseEmitter emitter : emitters) {
        try {
          emitter.send(SseEmitter.event().name("notification").data("new"));
        } catch (IOException | RuntimeException e) {
          remove(recipientSub, emitter);
        }
      }
    }
  }

  /** Sends a heartbeat comment to all live emitters so idle SSE connections are not reaped. */
  @Scheduled(fixedRateString = "${app.notifications.sse.heartbeat-interval:PT20S}")
  public void heartbeat() {
    emittersBySub.forEach(
        (recipientSub, emitters) ->
            emitters.forEach(
                emitter -> {
                  try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                  } catch (IOException | RuntimeException e) {
                    remove(recipientSub, emitter);
                  }
                }));
  }

  private void remove(@NotNull UUID recipientSub, @NotNull SseEmitter emitter) {
    Set<SseEmitter> emitters = emittersBySub.get(recipientSub);
    if (emitters != null) {
      emitters.remove(emitter);
      if (emitters.isEmpty()) {
        emittersBySub.remove(recipientSub);
      }
    }
  }
}
