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

import de.greluc.krt.iri.basetool.backend.model.BankBookingRequestType;
import de.greluc.krt.iri.basetool.backend.model.NotificationContextRole;
import de.greluc.krt.iri.basetool.backend.model.NotificationEventType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Domain event published right after an org-unit officer/lead's bank booking request is persisted
 * (epic #666 F2, REQ-BANK-026). Carries only the scalars the notification pipeline needs — the
 * target account id (drives the {@code ACCOUNT_GRANT} selector that resolves the employees granted
 * on that account), the render parameters, and the requesting actor to exclude — so the
 * after-commit listener never touches the managed {@link
 * de.greluc.krt.iri.basetool.backend.model.BankBookingRequest}.
 *
 * @param requestId the created request's id (also the notification's loose entity id)
 * @param accountId the target bank account id ({@code ACCOUNT_GRANT} selector input)
 * @param type whether the request is a deposit or a withdrawal
 * @param amount the requested whole-aUEC amount
 * @param accountNo the target account's human-readable number, for rendering
 * @param requesterHandle the requesting officer/lead's effective-name snapshot, for rendering
 * @param orgUnitShorthand the requesting org unit's shorthand, for rendering, or {@code null}
 * @param actorSub the requesting user's sub, excluded from recipients
 */
public record BankBookingRequestCreatedEvent(
    UUID requestId,
    UUID accountId,
    BankBookingRequestType type,
    BigDecimal amount,
    String accountNo,
    String requesterHandle,
    @Nullable String orgUnitShorthand,
    @Nullable UUID actorSub)
    implements NotificationEvent {

  /** Loose entity-type tag stored on the produced notifications for deep-linking. */
  public static final String ENTITY_TYPE = "BANK_BOOKING_REQUEST";

  @Override
  public NotificationEventType eventType() {
    return NotificationEventType.BANK_BOOKING_REQUEST_CREATED;
  }

  @Override
  public Map<NotificationContextRole, OrgUnitRef> contextOrgUnits() {
    return Map.of();
  }

  @Override
  public UUID contextAccountId() {
    return accountId;
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
    params.put("type", type.name());
    params.put("amount", amount.stripTrailingZeros().toPlainString());
    params.put("accountNo", accountNo);
    if (requesterHandle != null && !requesterHandle.isBlank()) {
      params.put("requester", requesterHandle);
    }
    if (orgUnitShorthand != null && !orgUnitShorthand.isBlank()) {
      params.put("orgUnit", orgUnitShorthand);
    }
    return params;
  }
}
