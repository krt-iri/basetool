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
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Domain event published right after a bank employee rejects a booking request (epic #666 F2,
 * REQ-BANK-026). It is directed at the requesting officer/lead so they learn the outcome and the
 * reason: the {@code EVENT_RECIPIENT} selector resolves to {@link #contextRecipientSub()} (the
 * requester), while the rejecting employee is the {@link #actorSub()}. Carries only scalars so the
 * after-commit listener never touches the managed request.
 *
 * @param requestId the rejected request's id (also the notification's loose entity id)
 * @param accountNo the target account's human-readable number, for rendering
 * @param amount the requested whole-aUEC amount, for rendering
 * @param reason the rejection reason, for rendering
 * @param requesterSub the requesting officer/lead's sub — the directed recipient
 * @param actorSub the rejecting employee's sub
 */
public record BankBookingRequestRejectedEvent(
    UUID requestId,
    String accountNo,
    BigDecimal amount,
    String reason,
    @Nullable UUID requesterSub,
    @Nullable UUID actorSub)
    implements NotificationEvent {

  /** Loose entity-type tag stored on the produced notifications for deep-linking. */
  public static final String ENTITY_TYPE = "BANK_BOOKING_REQUEST";

  @Override
  public NotificationEventType eventType() {
    return NotificationEventType.BANK_BOOKING_REQUEST_REJECTED;
  }

  @Override
  public Map<NotificationContextRole, OrgUnitRef> contextOrgUnits() {
    return Map.of();
  }

  @Override
  public UUID contextRecipientSub() {
    return requesterSub;
  }

  @Override
  public String entityType() {
    return ENTITY_TYPE;
  }

  @Override
  public UUID entityId() {
    return requestId;
  }

  @Override
  public Map<String, String> renderParams() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("accountNo", accountNo);
    params.put("amount", amount.stripTrailingZeros().toPlainString());
    if (reason != null && !reason.isBlank()) {
      params.put("reason", reason);
    }
    return params;
  }
}
