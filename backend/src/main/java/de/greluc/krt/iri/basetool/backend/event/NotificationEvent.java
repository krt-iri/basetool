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

package de.greluc.krt.iri.basetool.backend.event;

import de.greluc.krt.iri.basetool.backend.model.NotificationContextRole;
import de.greluc.krt.iri.basetool.backend.model.NotificationEventType;
import java.util.Map;
import java.util.UUID;

/**
 * Contract every notification-producing domain event implements.
 *
 * <p>Published via {@code ApplicationEventPublisher} inside the originating {@code @Transactional}
 * method and consumed after commit by the notification listener. Implementations carry only
 * immutable scalars (ids, kinds, render parameters) — never managed entities — so the listener can
 * run safely on another thread in a fresh transaction. A new producer adds one implementation; the
 * rule engine and creation pipeline need no changes.
 */
public interface NotificationEvent {

  /**
   * The trigger type, matched against {@code notification_rule.event_type}.
   *
   * @return the event type
   */
  NotificationEventType eventType();

  /**
   * The acting user's {@code sub}, excluded from recipients when a matching rule sets {@code
   * excludeActor}. {@code null} for anonymous/guest actors.
   *
   * @return the actor sub, or {@code null}
   */
  UUID actorSub();

  /**
   * The org units this event exposes by role, for {@code ORG_RELATIVE_ROLE} selector resolution.
   *
   * @return the context org units keyed by role; never {@code null}
   */
  Map<NotificationContextRole, OrgUnitRef> contextOrgUnits();

  /**
   * Loose type tag of the originating aggregate stored on each notification for deep-linking.
   *
   * @return the entity type tag (e.g. {@code JOB_ORDER})
   */
  String entityType();

  /**
   * Id of the originating aggregate stored on each notification for deep-linking.
   *
   * @return the entity id
   */
  UUID entityId();

  /**
   * Render parameters stored on each created notification so the frontend localizes the text.
   *
   * @return the i18n render parameters; never {@code null}, possibly empty
   */
  Map<String, String> renderParams();
}
