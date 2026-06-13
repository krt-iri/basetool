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

package de.greluc.krt.iri.basetool.backend.task;

import de.greluc.krt.iri.basetool.backend.service.BankLedgerIntegrityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled ledger-integrity sweep for the bank (REQ-BANK-020, epic #556 Phase 5; pattern: {@link
 * UserSyncTask}). Runs every {@code app.bank.integrity.interval} (default {@code PT1H}) and
 * delegates to {@link BankLedgerIntegrityService#verify()}, which logs each violation at {@code
 * ERROR}. The whole task is gated by {@code app.bank.integrity.enabled} (default {@code true}); set
 * it to {@code false} to disable the schedule (e.g. in tests that drive the verification directly).
 */
@Component
@ConditionalOnProperty(
    prefix = "app.bank.integrity",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class BankLedgerIntegrityTask {

  private final BankLedgerIntegrityService bankLedgerIntegrityService;

  /**
   * Fires the integrity verification on the configured schedule. Failures are logged and swallowed
   * so a transient DB hiccup never tears the scheduler thread down; the next tick retries.
   */
  @Scheduled(fixedDelayString = "${app.bank.integrity.interval:PT1H}")
  public void runIntegrityCheck() {
    log.info("Starting scheduled bank ledger integrity check...");
    try {
      BankLedgerIntegrityService.IntegrityReport report = bankLedgerIntegrityService.verify();
      log.info("Bank ledger integrity check finished — {} violation(s).", report.violationCount());
    } catch (Exception e) {
      log.error("Bank ledger integrity check failed to run", e);
    }
  }
}
