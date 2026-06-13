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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.greluc.krt.iri.basetool.backend.exception.BankConflictException;
import de.greluc.krt.iri.basetool.backend.model.BankAccount;
import de.greluc.krt.iri.basetool.backend.model.BankAccountStatus;
import de.greluc.krt.iri.basetool.backend.model.BankAccountType;
import de.greluc.krt.iri.basetool.backend.model.BankHolder;
import de.greluc.krt.iri.basetool.backend.model.BankTransaction;
import de.greluc.krt.iri.basetool.backend.model.BankTransactionType;
import de.greluc.krt.iri.basetool.backend.model.dto.BankTransactionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BankWipeResetResultDto;
import de.greluc.krt.iri.basetool.backend.model.dto.request.BankDepositRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.request.BankTransferRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.request.BankWithdrawalRequest;
import de.greluc.krt.iri.basetool.backend.model.projection.BankCounterLeg;
import de.greluc.krt.iri.basetool.backend.model.projection.BankHolderBalance;
import de.greluc.krt.iri.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankAuditEventRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankHolderRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankPostingRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for {@link BankLedgerService} against the real Testcontainers PostgreSQL:
 * double-entry invariants, the no-overdraft guard under real concurrent contention (account AND
 * holder level, REQ-BANK-006), the negated-mirror reversal (ADR-0010), the wipe reset
 * (REQ-BANK-013), append-only behavior and the one-audit-row-per-booking rule (REQ-BANK-012).
 */
@SpringBootTest
@ActiveProfiles("test")
class BankLedgerServiceTest {

  private static final int THREADS = 4;
  private static final int START_TIMEOUT_SECONDS = 5;
  private static final int FINISH_TIMEOUT_SECONDS = 60;

  @Autowired private BankLedgerService bankLedgerService;
  @Autowired private BankAccountRepository accountRepository;
  @Autowired private BankHolderRepository holderRepository;
  @Autowired private BankTransactionRepository transactionRepository;
  @Autowired private BankPostingRepository postingRepository;
  @Autowired private BankAuditEventRepository auditEventRepository;

  private BankAccount account;
  private BankAccount otherAccount;
  private BankHolder holderA;
  private BankHolder holderB;

  /** Seeds two fresh SPECIAL accounts and two holders per test (unique names per run). */
  @BeforeEach
  void seed() {
    account = newAccount("Test Konto " + UUID.randomUUID());
    otherAccount = newAccount("Gegenkonto " + UUID.randomUUID());
    holderA = newHolder("holder-a-" + UUID.randomUUID());
    holderB = newHolder("holder-b-" + UUID.randomUUID());
  }

  @Test
  void bookDeposit_createsPositivePostingAndExactlyOneAuditRow() {
    // Given
    long auditBefore = auditEventRepository.count();

    // When
    BankTransactionDto tx =
        bankLedgerService.bookDeposit(
            new BankDepositRequest(
                account.getId(), holderA.getId(), new BigDecimal("500"), "seed"));

    // Then
    assertEquals(BankTransactionType.DEPOSIT, tx.type());
    assertEquals(0, balance(account).compareTo(new BigDecimal("500")));
    assertEquals(
        0,
        postingRepository
            .holderSubBalance(account.getId(), holderA.getId())
            .compareTo(new BigDecimal("500")));
    assertEquals(auditBefore + 1, auditEventRepository.count(), "exactly one audit row");
    assertTrue(auditEventRepository.existsByTransactionId(tx.id()));
  }

  @Test
  void bookWithdrawal_rejectsOverdraftWithStableCode() {
    // Given
    deposit(account, holderA, "100");

    // When
    BankConflictException ex =
        assertThrows(
            BankConflictException.class,
            () ->
                bankLedgerService.bookWithdrawal(
                    new BankWithdrawalRequest(
                        account.getId(), holderA.getId(), new BigDecimal("250"), null)));

    // Then
    assertEquals(BankConflictException.CODE_BANK_HOLDER_OVERDRAFT, ex.getCode());
    assertEquals(0, balance(account).compareTo(new BigDecimal("100")), "balance unchanged");
  }

  @Test
  void bookWithdrawal_rejectsHolderOverdraftEvenWhenAccountBalanceSuffices() {
    // Given: account holds 1000, but only 300 with holder A
    deposit(account, holderA, "300");
    deposit(account, holderB, "700");

    // When
    BankConflictException ex =
        assertThrows(
            BankConflictException.class,
            () ->
                bankLedgerService.bookWithdrawal(
                    new BankWithdrawalRequest(
                        account.getId(), holderA.getId(), new BigDecimal("400"), null)));

    // Then
    assertEquals(BankConflictException.CODE_BANK_HOLDER_OVERDRAFT, ex.getCode());
    assertEquals("300", ex.getProperties().get("available"));
  }

  @Test
  void bookTransfer_betweenAccounts_legsSumToZeroAndDistributionsMatch() {
    // Given
    deposit(account, holderA, "1000");

    // When
    BankTransactionDto tx =
        bankLedgerService.bookTransfer(
            new BankTransferRequest(
                account.getId(),
                holderA.getId(),
                otherAccount.getId(),
                holderB.getId(),
                new BigDecimal("400"),
                "Bereichsanteil"),
            true);

    // Then
    List<BankCounterLeg> legs = postingRepository.findLegsByTransactionIds(List.of(tx.id()));
    assertEquals(2, legs.size());
    BigDecimal sum =
        legs.stream().map(BankCounterLeg::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertEquals(0, sum.signum(), "TRANSFER legs must sum to zero");
    assertEquals(0, balance(account).compareTo(new BigDecimal("600")));
    assertEquals(0, balance(otherAccount).compareTo(new BigDecimal("400")));
  }

  @Test
  void bookTransfer_intraAccountRebooking_movesCustodyNotBalance() {
    // Given
    deposit(account, holderA, "800");
    BigDecimal balanceBefore = balance(account);

    // When
    bankLedgerService.bookTransfer(
        new BankTransferRequest(
            account.getId(),
            holderA.getId(),
            account.getId(),
            holderB.getId(),
            new BigDecimal("300"),
            "Uebergabe"),
        true);

    // Then
    assertEquals(0, balance(account).compareTo(balanceBefore), "balance unchanged");
    assertEquals(
        0,
        postingRepository
            .holderSubBalance(account.getId(), holderA.getId())
            .compareTo(new BigDecimal("500")));
    assertEquals(
        0,
        postingRepository
            .holderSubBalance(account.getId(), holderB.getId())
            .compareTo(new BigDecimal("300")));
  }

  @Test
  void bookTransfer_rejectsSelfTransfer() {
    // Given
    deposit(account, holderA, "100");

    // When / Then
    BankConflictException ex =
        assertThrows(
            BankConflictException.class,
            () ->
                bankLedgerService.bookTransfer(
                    new BankTransferRequest(
                        account.getId(),
                        holderA.getId(),
                        account.getId(),
                        holderA.getId(),
                        new BigDecimal("50"),
                        null),
                    true));
    assertEquals(BankConflictException.CODE_BANK_SELF_TRANSFER, ex.getCode());
  }

  @Test
  void bookDeposit_rejectsClosedAccount() {
    // Given
    account.setStatus(BankAccountStatus.CLOSED);
    accountRepository.save(account);

    // When / Then
    BankConflictException ex =
        assertThrows(
            BankConflictException.class,
            () ->
                bankLedgerService.bookDeposit(
                    new BankDepositRequest(
                        account.getId(), holderA.getId(), new BigDecimal("10"), null)));
    assertEquals(BankConflictException.CODE_BANK_ACCOUNT_CLOSED, ex.getCode());
  }

  @Test
  void bookDeposit_rejectsInactiveHolder() {
    // Given
    holderA.setActive(false);
    holderRepository.save(holderA);

    // When / Then
    BankConflictException ex =
        assertThrows(
            BankConflictException.class,
            () ->
                bankLedgerService.bookDeposit(
                    new BankDepositRequest(
                        account.getId(), holderA.getId(), new BigDecimal("10"), null)));
    assertEquals(BankConflictException.CODE_BANK_HOLDER_INACTIVE, ex.getCode());
  }

  @Test
  void reverseTransaction_createsNegatedMirrorAndKeepsOriginalUntouched() {
    // Given
    deposit(account, holderA, "1000");
    BankTransactionDto transfer =
        bankLedgerService.bookTransfer(
            new BankTransferRequest(
                account.getId(),
                holderA.getId(),
                otherAccount.getId(),
                holderB.getId(),
                new BigDecimal("400"),
                null),
            true);
    List<BankCounterLeg> originalLegs =
        postingRepository.findLegsByTransactionIds(List.of(transfer.id()));
    long postingsBefore = postingRepository.count();

    // When
    BankTransactionDto reversal =
        bankLedgerService.reverseTransaction(transfer.id(), "Tippfehler korrigiert");

    // Then: negated mirror, original untouched, nothing deleted (append-only)
    List<BankCounterLeg> reversalLegs =
        postingRepository.findLegsByTransactionIds(List.of(reversal.id()));
    assertEquals(originalLegs.size(), reversalLegs.size());
    for (BankCounterLeg original : originalLegs) {
      assertTrue(
          reversalLegs.stream()
              .anyMatch(
                  mirrored ->
                      mirrored.accountId().equals(original.accountId())
                          && mirrored.holderId().equals(original.holderId())
                          && mirrored.amount().compareTo(original.amount().negate()) == 0),
          "every original leg must have a negated mirror");
    }
    assertEquals(postingsBefore + reversalLegs.size(), postingRepository.count());
    assertEquals(
        originalLegs.size(),
        postingRepository.findLegsByTransactionIds(List.of(transfer.id())).size(),
        "original legs survive unchanged");
    assertEquals(0, balance(account).compareTo(new BigDecimal("1000")), "balances restored");
    assertEquals(0, balance(otherAccount).signum());
  }

  @Test
  void reverseTransaction_rejectsSecondReversal() {
    // Given
    BankTransactionDto deposit = deposit(account, holderA, "100");
    bankLedgerService.reverseTransaction(deposit.id(), null);

    // When / Then
    BankConflictException ex =
        assertThrows(
            BankConflictException.class,
            () -> bankLedgerService.reverseTransaction(deposit.id(), null));
    assertEquals(BankConflictException.CODE_BANK_ALREADY_REVERSED, ex.getCode());
  }

  @Test
  void reverseTransaction_rejectsWhenMoneyAlreadyMovedOn() {
    // Given: deposit 100 to A, then A pays everything out — undoing the deposit would overdraw
    BankTransactionDto deposit = deposit(account, holderA, "100");
    bankLedgerService.bookWithdrawal(
        new BankWithdrawalRequest(account.getId(), holderA.getId(), new BigDecimal("100"), null));

    // When / Then
    BankConflictException ex =
        assertThrows(
            BankConflictException.class,
            () -> bankLedgerService.reverseTransaction(deposit.id(), null));
    assertEquals(BankConflictException.CODE_BANK_HOLDER_OVERDRAFT, ex.getCode());
  }

  @Test
  void reverseTransaction_rejectsReversingAWipeReset() {
    // Given: a WIPE_RESET transaction (a deliberate end-state, REQ-BANK-013)
    BankTransaction wipe =
        transactionRepository.save(
            BankTransaction.builder()
                .type(BankTransactionType.WIPE_RESET)
                .createdAt(Instant.now())
                .build());

    // When / Then: it cannot itself be reversed (REQ-BANK-004)
    BankConflictException ex =
        assertThrows(
            BankConflictException.class,
            () -> bankLedgerService.reverseTransaction(wipe.getId(), null));
    assertEquals(BankConflictException.CODE_BANK_NOT_REVERSIBLE, ex.getCode());
  }

  @Test
  void reverseTransaction_rejectsReversingAReversal() {
    // Given: a deposit and its reversal
    BankTransactionDto deposit = deposit(account, holderA, "100");
    BankTransactionDto reversal = bankLedgerService.reverseTransaction(deposit.id(), null);

    // When / Then: the reversal itself is not reversible — reverse the original instead
    BankConflictException ex =
        assertThrows(
            BankConflictException.class,
            () -> bankLedgerService.reverseTransaction(reversal.id(), null));
    assertEquals(BankConflictException.CODE_BANK_NOT_REVERSIBLE, ex.getCode());
  }

  @Test
  void resetAllBalances_zeroesEveryStashKeepsHistoryAndIsIdempotent() {
    // Given (the shared test database may hold residue from sibling tests, so all assertions
    // are relative to THIS test's accounts or pin the global all-zero end state)
    deposit(account, holderA, "300");
    deposit(account, holderB, "700");
    deposit(otherAccount, holderB, "250");
    long postingsBefore = postingRepository.count();

    // When
    BankWipeResetResultDto result = bankLedgerService.resetAllBalances();

    // Then
    assertTrue(result.accountsReset() >= 2, "both seeded accounts were reset");
    assertTrue(result.holderStashesZeroed() >= 3, "all three seeded stashes were zeroed");
    assertTrue(
        result.totalZeroed().compareTo(new BigDecimal("1250")) >= 0,
        "the total covers at least this test's 1250 aUEC");
    assertEquals(0, balance(account).signum());
    assertEquals(0, balance(otherAccount).signum());
    for (BankHolderBalance slice : postingRepository.holderDistribution(account.getId())) {
      assertEquals(0, slice.amount().signum(), "every sub-balance is zero");
    }
    assertTrue(postingRepository.count() > postingsBefore, "history preserved, postings added");

    // And: a second run is a global no-op (the first run zeroed the entire bank)
    BankWipeResetResultDto second = bankLedgerService.resetAllBalances();
    assertEquals(0, second.accountsReset());
    assertEquals(0, second.totalZeroed().signum());
  }

  @Test
  void holderDistribution_alwaysSumsToAccountBalance() {
    // Given
    deposit(account, holderA, "321");
    deposit(account, holderB, "679");
    bankLedgerService.bookTransfer(
        new BankTransferRequest(
            account.getId(),
            holderB.getId(),
            account.getId(),
            holderA.getId(),
            new BigDecimal("79"),
            null),
        true);

    // When
    BigDecimal distributionSum =
        postingRepository.holderDistribution(account.getId()).stream()
            .map(BankHolderBalance::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Then
    assertEquals(0, distributionSum.compareTo(balance(account)));
  }

  @Test
  void concurrentWithdrawals_cannotJointlyOverdrawTheAccount() throws Exception {
    // Given: 500 on the account; 4 threads withdraw 200 each — at most 2 can succeed
    deposit(account, holderA, "500");
    CountDownLatch ready = new CountDownLatch(THREADS);
    CountDownLatch go = new CountDownLatch(1);
    AtomicInteger success = new AtomicInteger();
    AtomicInteger conflict = new AtomicInteger();

    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      for (int i = 0; i < THREADS; i++) {
        pool.submit(
            () -> {
              ready.countDown();
              try {
                if (!go.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                  return;
                }
                bankLedgerService.bookWithdrawal(
                    new BankWithdrawalRequest(
                        account.getId(), holderA.getId(), new BigDecimal("200"), null));
                success.incrementAndGet();
              } catch (BankConflictException expected) {
                conflict.incrementAndGet();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });
      }
      assertTrue(ready.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS));
      go.countDown();
      pool.shutdown();
      assertTrue(pool.awaitTermination(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }

    // Then: exactly 2 succeed (2 x 200 = 400 <= 500; a third would overdraw)
    assertEquals(2, success.get(), "exactly two withdrawals fit into the balance");
    assertEquals(THREADS - 2, conflict.get());
    assertEquals(0, balance(account).compareTo(new BigDecimal("100")));
  }

  @Test
  void concurrentHolderWithdrawals_cannotJointlyOverdrawTheStash() throws Exception {
    // Given: holder A holds 300, holder B 700 — the ACCOUNT could cover both 250-withdrawals,
    // the STASH of A covers only one (REQ-BANK-006 holder level)
    deposit(account, holderA, "300");
    deposit(account, holderB, "700");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    AtomicInteger success = new AtomicInteger();
    AtomicInteger conflict = new AtomicInteger();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int i = 0; i < 2; i++) {
        pool.submit(
            () -> {
              ready.countDown();
              try {
                if (!go.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                  return;
                }
                bankLedgerService.bookWithdrawal(
                    new BankWithdrawalRequest(
                        account.getId(), holderA.getId(), new BigDecimal("250"), null));
                success.incrementAndGet();
              } catch (BankConflictException expected) {
                conflict.incrementAndGet();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });
      }
      assertTrue(ready.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS));
      go.countDown();
      pool.shutdown();
      assertTrue(pool.awaitTermination(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }

    // Then
    assertEquals(1, success.get(), "only one withdrawal fits into holder A's stash");
    assertEquals(1, conflict.get());
    assertEquals(
        0,
        postingRepository
            .holderSubBalance(account.getId(), holderA.getId())
            .compareTo(new BigDecimal("50")));
  }

  @Test
  void auditRows_oneRowPerSuccessfulBookingAndNoneForRejections() {
    // Given
    long before = auditEventRepository.count();

    // When: deposit + withdrawal + transfer + rebooking succeed (4 mutations); the reversal of
    // the deposit is rejected (holder A no longer covers the original 1000) and must NOT audit
    BankTransactionDto deposit = deposit(account, holderA, "1000");
    bankLedgerService.bookWithdrawal(
        new BankWithdrawalRequest(account.getId(), holderA.getId(), new BigDecimal("100"), null));
    bankLedgerService.bookTransfer(
        new BankTransferRequest(
            account.getId(),
            holderA.getId(),
            otherAccount.getId(),
            holderB.getId(),
            new BigDecimal("200"),
            null),
        true);
    bankLedgerService.bookTransfer(
        new BankTransferRequest(
            account.getId(),
            holderA.getId(),
            account.getId(),
            holderB.getId(),
            new BigDecimal("50"),
            null),
        true);
    assertThrows(
        BankConflictException.class,
        () -> bankLedgerService.reverseTransaction(deposit.id(), null));

    // Then
    assertEquals(before + 4, auditEventRepository.count(), "one audit row per successful booking");
  }

  /** Books a deposit through the service (the canonical seeding path). */
  private BankTransactionDto deposit(BankAccount target, BankHolder holder, String amount) {
    return bankLedgerService.bookDeposit(
        new BankDepositRequest(target.getId(), holder.getId(), new BigDecimal(amount), null));
  }

  private BigDecimal balance(BankAccount target) {
    return postingRepository.accountBalance(target.getId());
  }

  private BankAccount newAccount(String name) {
    BankAccount a = new BankAccount();
    a.setAccountNo(String.format("KB-%04d", accountRepository.nextAccountNoValue()));
    a.setName(name);
    a.setType(BankAccountType.SPECIAL);
    a.setStatus(BankAccountStatus.ACTIVE);
    BankAccount saved = accountRepository.save(a);
    assertNotNull(saved.getId());
    return saved;
  }

  private BankHolder newHolder(String handle) {
    BankHolder h = new BankHolder();
    h.setHandle(handle);
    h.setActive(true);
    return holderRepository.save(h);
  }
}
