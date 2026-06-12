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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.greluc.krt.iri.basetool.backend.model.BankAccount;
import de.greluc.krt.iri.basetool.backend.model.BankAccountStatus;
import de.greluc.krt.iri.basetool.backend.model.BankAccountType;
import de.greluc.krt.iri.basetool.backend.model.BankHolder;
import de.greluc.krt.iri.basetool.backend.model.BankPosting;
import de.greluc.krt.iri.basetool.backend.model.BankTransaction;
import de.greluc.krt.iri.basetool.backend.model.BankTransactionType;
import de.greluc.krt.iri.basetool.backend.model.dto.request.BankDepositRequest;
import de.greluc.krt.iri.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankHolderRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankPostingRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for {@link BankLedgerIntegrityService} against the real Testcontainers
 * PostgreSQL (REQ-BANK-020): a clean ledger built through the service passes; a synthetically
 * corrupted ledger — a raw posting inserted past the service guards — is flagged.
 */
@SpringBootTest
@ActiveProfiles("test")
class BankLedgerIntegrityServiceTest {

  @Autowired private BankLedgerIntegrityService integrityService;
  @Autowired private BankLedgerService bankLedgerService;
  @Autowired private BankAccountRepository accountRepository;
  @Autowired private BankHolderRepository holderRepository;
  @Autowired private BankTransactionRepository transactionRepository;
  @Autowired private BankPostingRepository postingRepository;

  private BankAccount account;
  private BankHolder holder;

  @BeforeEach
  void seed() {
    account = newAccount("Integrity Konto " + UUID.randomUUID());
    holder = newHolder("integrity-holder-" + UUID.randomUUID());
  }

  @Test
  void verify_passesForALedgerBuiltThroughTheService() {
    // Given: a clean deposit/transfer history via the guarded service
    bankLedgerService.bookDeposit(
        new BankDepositRequest(account.getId(), holder.getId(), new BigDecimal("500"), "seed"));

    // When
    BankLedgerIntegrityService.IntegrityReport report = integrityService.verify();

    // Then: no violation references our fresh account
    assertNotNull(report);
    assertFalse(
        report.negativeAccountBalances().contains(account.getId()),
        "a service-built account must never be flagged negative");
  }

  @Test
  void verify_flagsASyntheticallyCorruptedNegativeBalance() {
    // Given: a raw negative posting inserted past the no-overdraft guard on an empty account
    BankTransaction tx =
        transactionRepository.save(
            BankTransaction.builder()
                .type(BankTransactionType.WITHDRAWAL)
                .createdAt(Instant.now())
                .build());
    postingRepository.save(
        BankPosting.builder()
            .transaction(tx)
            .account(account)
            .holder(holder)
            .amount(new BigDecimal("-1000"))
            .createdAt(Instant.now())
            .build());

    // When
    BankLedgerIntegrityService.IntegrityReport report = integrityService.verify();

    // Then
    assertFalse(report.isSound(), "the corrupted ledger must not be reported sound");
    assertTrue(
        report.negativeAccountBalances().contains(account.getId()),
        "the negative account balance must be flagged");
    assertTrue(report.violationCount() >= 1);
  }

  @Test
  void verify_flagsAnUnbalancedTransferTransaction() {
    // Given: a TRANSFER header with a single non-zeroing leg (legs must sum to zero)
    BankTransaction tx =
        transactionRepository.save(
            BankTransaction.builder()
                .type(BankTransactionType.TRANSFER)
                .createdAt(Instant.now())
                .build());
    postingRepository.save(
        BankPosting.builder()
            .transaction(tx)
            .account(account)
            .holder(holder)
            .amount(new BigDecimal("250"))
            .createdAt(Instant.now())
            .build());

    // When
    BankLedgerIntegrityService.IntegrityReport report = integrityService.verify();

    // Then
    assertTrue(
        report.unbalancedTransfers().contains(tx.getId()),
        "the one-legged transfer must be flagged as not summing to zero");
  }

  private BankAccount newAccount(String name) {
    BankAccount a = new BankAccount();
    a.setAccountNo(String.format("KB-%04d", accountRepository.nextAccountNoValue()));
    a.setName(name);
    a.setType(BankAccountType.SPECIAL);
    a.setStatus(BankAccountStatus.ACTIVE);
    return accountRepository.save(a);
  }

  private BankHolder newHolder(String handle) {
    BankHolder h = new BankHolder();
    h.setHandle(handle);
    h.setActive(true);
    return holderRepository.save(h);
  }
}
