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

package de.greluc.krt.profit.basetool.backend.service;

import de.greluc.krt.profit.basetool.backend.event.NotificationEvent;
import de.greluc.krt.profit.basetool.backend.model.Notification;
import de.greluc.krt.profit.basetool.backend.model.NotificationType;
import de.greluc.krt.profit.basetool.backend.repository.NotificationRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the notification rows a fired {@link NotificationEvent} produces.
 *
 * <p>Runs in its own transaction off the request thread (the after-commit async listener calls it),
 * so it deliberately never touches the originating aggregate — it works purely from the event's
 * scalars, which keeps it clear of the optimistic-locking traps in {@code CLAUDE.md} (no second
 * {@code @Version} bump on the job order). One row is written per resolved recipient; an event that
 * resolves to nobody writes nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationCreationService {

  private final RuleEvaluationService ruleEvaluationService;
  private final NotificationRepository notificationRepository;
  private final NotificationParamsCodec notificationParamsCodec;
  private final NotificationStreamService notificationStreamService;

  /**
   * Resolves recipients for the event and writes one notification per recipient per produced type.
   *
   * @param event the fired event
   * @return the number of notification rows created
   */
  @Transactional
  public int createFromEvent(@NotNull NotificationEvent event) {
    Map<NotificationType, Set<UUID>> recipientsByType =
        ruleEvaluationService.resolveRecipients(event);
    if (recipientsByType.isEmpty()) {
      log.debug(
          "Event {} for entity {} resolved no recipients", event.eventType(), event.entityId());
      return 0;
    }
    String paramsJson = notificationParamsCodec.serialize(event.renderParams());
    List<Notification> toCreate = new ArrayList<>();
    for (Map.Entry<NotificationType, Set<UUID>> entry : recipientsByType.entrySet()) {
      NotificationType type = entry.getKey();
      for (UUID recipientSub : entry.getValue()) {
        toCreate.add(
            Notification.builder()
                .recipientSub(recipientSub)
                .type(type)
                .params(paramsJson)
                .entityType(event.entityType())
                .entityId(event.entityId())
                .read(false)
                .build());
      }
    }
    notificationRepository.saveAll(toCreate);
    log.info(
        "Created {} notification(s) for event {} entity {}",
        toCreate.size(),
        event.eventType(),
        event.entityId());
    pushRealtime(recipientsByType);
    return toCreate.size();
  }

  /**
   * Best-effort real-time push: notifies the live SSE subscribers of every recipient so their
   * client refreshes its unread state. A push failure is swallowed — the frontend polling fallback
   * keeps the unread badge correct regardless (REQ-NOTIF-010).
   *
   * @param recipientsByType the resolved recipients grouped by produced type
   */
  private void pushRealtime(Map<NotificationType, Set<UUID>> recipientsByType) {
    Set<UUID> recipientSubs = new HashSet<>();
    recipientsByType.values().forEach(recipientSubs::addAll);
    try {
      notificationStreamService.publish(recipientSubs);
    } catch (RuntimeException e) {
      log.debug("Real-time notification push failed; polling fallback remains", e);
    }
  }
}
