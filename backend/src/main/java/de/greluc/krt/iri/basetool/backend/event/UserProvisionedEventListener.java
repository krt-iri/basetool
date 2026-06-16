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

import de.greluc.krt.iri.basetool.backend.service.DefaultBlueprintProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Grants the default blueprints to a user the moment their {@code app_user} row is first created
 * (REQ-INV-016), giving a brand-new user their defaults immediately instead of waiting for the
 * periodic provisioning sweep.
 *
 * <p>Fires only {@code AFTER_COMMIT} of the user-creating transaction (so a rolled-back first-login
 * sync never grants phantom rows) and runs in its own transaction. The grant is idempotent ({@code
 * ON CONFLICT DO NOTHING}), so the converter's retry-on-optimistic-lock and the overlapping
 * periodic sweep can never double-insert. Any failure is swallowed and logged: the user row has
 * already committed, and the next sweep will retry the grant.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserProvisionedEventListener {

  private final DefaultBlueprintProvisioningService provisioningService;

  /**
   * Grants the default blueprints to the newly provisioned user after their creating transaction
   * commits.
   *
   * @param event the published user-provisioned event
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserProvisioned(UserProvisionedEvent event) {
    try {
      provisioningService.grantDefaultsToUser(event.userId().toString());
    } catch (RuntimeException e) {
      log.error(
          "Failed to grant default blueprints to newly provisioned user {}", event.userId(), e);
    }
  }
}
