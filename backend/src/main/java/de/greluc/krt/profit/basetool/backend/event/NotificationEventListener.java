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

package de.greluc.krt.profit.basetool.backend.event;

import de.greluc.krt.profit.basetool.backend.config.AsyncConfig;
import de.greluc.krt.profit.basetool.backend.service.NotificationCreationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges published {@link NotificationEvent}s to notification creation.
 *
 * <p>Fires only {@code AFTER_COMMIT} of the originating transaction (so a rolled-back business
 * action never produces phantom notifications) and runs on the dedicated {@link
 * AsyncConfig#NOTIFICATION_EXECUTOR} thread pool (so creation never adds latency to the request).
 * Any failure is swallowed and logged: the business transaction has already committed, so a
 * notification hiccup must not surface to the user or be retried inline.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

  private final NotificationCreationService notificationCreationService;

  /**
   * Creates notifications for an event after its originating transaction commits.
   *
   * @param event the published notification event
   */
  @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onNotificationEvent(NotificationEvent event) {
    try {
      notificationCreationService.createFromEvent(event);
    } catch (RuntimeException e) {
      log.error(
          "Failed to create notifications for event {} entity {}",
          event.eventType(),
          event.entityId(),
          e);
    }
  }
}
