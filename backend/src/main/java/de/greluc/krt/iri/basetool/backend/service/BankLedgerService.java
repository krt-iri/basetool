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

import de.greluc.krt.iri.basetool.backend.exception.BankConflictException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.BankAccount;
import de.greluc.krt.iri.basetool.backend.model.BankAccountStatus;
import de.greluc.krt.iri.basetool.backend.model.BankAuditEventType;
import de.greluc.krt.iri.basetool.backend.model.BankHolder;
import de.greluc.krt.iri.basetool.backend.model.BankPosting;
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
import de.greluc.krt.iri.basetool.backend.repository.BankHolderRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankPostingRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bank's booking engine (epic #556): books deposits, withdrawals, transfers (incl.
 * intra-account holder rebookings), reversals and the admin wipe reset onto the append-only
 * double-entry ledger (REQ-BANK-004, ADR-0010).
 *
 * <p><strong>Concurrency contract.</strong> Every booking first locks the affected account row(s)
 * via {@code findByIdForUpdate} — multi-account bookings in ascending id order so concurrent flows
 * cannot deadlock — and only then reads the balances it validates against. Because all value
 * movement on an account serializes on that lock, the no-overdraft invariant (REQ-BANK-006, account
 * level AND per-(account, holder) sub-balance) cannot be raced. The ledger rows themselves are
 * insert-only: no {@code @Version} churn, no {@code save()}-on-managed-entity traps (see the
 * CLAUDE.md concurrency section the design descends from).
 *
 * <p><strong>Holder activity.</strong> Postings that ADD money to a holder's stash require the
 * holder to be active; postings that REMOVE money are allowed on deactivated holders so a stash can
 * be wound down. Reversals are exempt — they restore a prior, already-audited state.
 *
 * <p>Every booking appends exactly one audit row in the same transaction ({@link
 * BankAuditService}); an audit failure rolls the booking back (REQ-BANK-012).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BankLedgerService {

  private final BankAccountRepository accountRepository;
  private final BankHolderRepository holderRepository;
  private final BankTransactionRepository transactionRepository;
  private final BankPostingRepository postingRepository;
  private final BankAuditService bankAuditService;
  private final AuthHelperService authHelperService;

  /**
   * Books a deposit (REQ-BANK-004): one positive posting on the receiving account naming the holder
   * who physically received the money.
   *
   * @param request validated deposit payload
   * @return acknowledgement of the created transaction
   * @throws NotFoundException when account or holder do not exist
   * @throws BankConflictException with {@code BANK_ACCOUNT_CLOSED} on a closed account or {@code
   *     BANK_HOLDER_INACTIVE} on a deactivated holder
   */
  @Transactional
  public BankTransactionDto bookDeposit(@NotNull BankDepositRequest request) {
    BankAccount account = lockAccount(request.accountId());
    requireActive(account);
    BankHolder holder = requireHolder(request.holderId());
    requireActiveHolder(holder);

    Instant now = Instant.now();
    BankTransaction tx = persistTransaction(BankTransactionType.DEPOSIT, request.note(), null, now);
    persistPosting(tx, account, holder, request.amount(), now);
    bankAuditService.record(
        BankAuditEventType.DEPOSIT_BOOKED,
        account.getId(),
        tx.getId(),
        null,
        "+" + request.amount().toPlainString() + " aUEC @" + holder.getHandle());
    return toDto(tx);
  }

  /**
   * Books a withdrawal (REQ-BANK-004): one negative posting on the paying account naming the holder
   * who physically paid the money out, guarded by the no-overdraft rule at account and holder level
   * (REQ-BANK-006).
   *
   * @param request validated withdrawal payload
   * @return acknowledgement of the created transaction
   * @throws NotFoundException when account or holder do not exist
   * @throws BankConflictException with {@code BANK_ACCOUNT_CLOSED}, {@code BANK_OVERDRAFT} or
   *     {@code BANK_HOLDER_OVERDRAFT}
   */
  @Transactional
  public BankTransactionDto bookWithdrawal(@NotNull BankWithdrawalRequest request) {
    BankAccount account = lockAccount(request.accountId());
    requireActive(account);
    BankHolder holder = requireHolder(request.holderId());
    requireCoverage(account, holder, request.amount());

    Instant now = Instant.now();
    BankTransaction tx =
        persistTransaction(BankTransactionType.WITHDRAWAL, request.note(), null, now);
    persistPosting(tx, account, holder, request.amount().negate(), now);
    bankAuditService.record(
        BankAuditEventType.WITHDRAWAL_BOOKED,
        account.getId(),
        tx.getId(),
        null,
        "-" + request.amount().toPlainString() + " aUEC @" + holder.getHandle());
    return toDto(tx);
  }

  /**
   * Books a transfer (REQ-BANK-011): two postings summing to zero. Same source and destination
   * account = intra-account holder rebooking (custody moves, the balance does not); different
   * accounts = account-to-account transfer. The caller's {@code can_transfer} on the source is
   * gated at the controller; the <em>destination visibility</em> rule (the employee must hold any
   * grant on the destination account, REQ-BANK-011 variant 1) is enforced here via the supplied
   * visibility check.
   *
   * @param request validated transfer payload
   * @param destinationVisible whether the caller may see the destination account (pre-computed by
   *     the controller from {@code BankSecurityService.canSee}); ignored for intra-account
   *     rebookings where source visibility already implies it
   * @return acknowledgement of the created transaction
   * @throws NotFoundException when an account or holder does not exist
   * @throws AccessDeniedException when the destination is not visible to the caller
   * @throws BankConflictException with {@code BANK_SELF_TRANSFER}, {@code BANK_ACCOUNT_CLOSED},
   *     {@code BANK_HOLDER_INACTIVE}, {@code BANK_OVERDRAFT} or {@code BANK_HOLDER_OVERDRAFT}
   */
  @Transactional
  public BankTransactionDto bookTransfer(
      @NotNull BankTransferRequest request, boolean destinationVisible) {
    boolean intraAccount = request.sourceAccountId().equals(request.destinationAccountId());
    if (intraAccount && request.sourceHolderId().equals(request.destinationHolderId())) {
      throw new BankConflictException(
          BankConflictException.CODE_BANK_SELF_TRANSFER,
          "Source and destination of a transfer must differ");
    }
    if (!intraAccount && !destinationVisible) {
      throw new AccessDeniedException("Destination account is not visible to the caller");
    }

    BankAccount source;
    BankAccount destination;
    if (intraAccount) {
      source = lockAccount(request.sourceAccountId());
      destination = source;
    } else if (request.sourceAccountId().compareTo(request.destinationAccountId()) < 0) {
      source = lockAccount(request.sourceAccountId());
      destination = lockAccount(request.destinationAccountId());
    } else {
      destination = lockAccount(request.destinationAccountId());
      source = lockAccount(request.sourceAccountId());
    }
    requireActive(source);
    requireActive(destination);

    BankHolder sourceHolder = requireHolder(request.sourceHolderId());
    BankHolder destinationHolder = requireHolder(request.destinationHolderId());
    requireActiveHolder(destinationHolder);
    requireCoverage(source, sourceHolder, request.amount());

    Instant now = Instant.now();
    BankTransaction tx =
        persistTransaction(BankTransactionType.TRANSFER, request.note(), null, now);
    persistPosting(tx, source, sourceHolder, request.amount().negate(), now);
    persistPosting(tx, destination, destinationHolder, request.amount(), now);

    if (intraAccount) {
      bankAuditService.record(
          BankAuditEventType.HOLDER_REBOOKED,
          source.getId(),
          tx.getId(),
          null,
          request.amount().toPlainString()
              + " aUEC "
              + sourceHolder.getHandle()
              + " -> "
              + destinationHolder.getHandle());
    } else {
      bankAuditService.record(
          BankAuditEventType.TRANSFER_BOOKED,
          source.getId(),
          tx.getId(),
          null,
          request.amount().toPlainString()
              + " aUEC -> "
              + destination.getAccountNo()
              + " ("
              + sourceHolder.getHandle()
              + " -> "
              + destinationHolder.getHandle()
              + ")");
    }
    return toDto(tx);
  }

  /**
   * Reverses a transaction (REQ-BANK-004): books a {@code REVERSAL} whose legs are the negated
   * mirror of the original's legs (ADR-0010), referencing the original. A transaction can be
   * reversed at most once; the reversal itself must satisfy the no-overdraft rule (undoing a
   * deposit whose money has meanwhile moved on is rejected, not forced negative).
   *
   * @param transactionId the transaction to reverse
   * @param note optional correction note
   * @return acknowledgement of the created reversal
   * @throws NotFoundException when the transaction does not exist
   * @throws BankConflictException with {@code BANK_ALREADY_REVERSED}, {@code BANK_ACCOUNT_CLOSED},
   *     {@code BANK_OVERDRAFT} or {@code BANK_HOLDER_OVERDRAFT}
   */
  @Transactional
  public BankTransactionDto reverseTransaction(@NotNull UUID transactionId, @Nullable String note) {
    final BankTransaction original =
        transactionRepository
            .findById(transactionId)
            .orElseThrow(() -> new NotFoundException("Bank transaction not found"));
    if (transactionRepository.existsByReversedTransactionId(transactionId)) {
      throw new BankConflictException(
          BankConflictException.CODE_BANK_ALREADY_REVERSED,
          "The transaction has already been reversed");
    }
    List<BankCounterLeg> legs = postingRepository.findLegsByTransactionIds(List.of(transactionId));

    Map<UUID, BankAccount> lockedAccounts =
        legs.stream()
            .map(BankCounterLeg::accountId)
            .distinct()
            .sorted()
            .collect(
                java.util.stream.Collectors.toMap(
                    id -> id, this::lockAccount, (a, b) -> a, java.util.LinkedHashMap::new));
    lockedAccounts.values().forEach(this::requireActive);

    // Validate the negated mirror against the current balances: a leg that was positive becomes
    // a removal and must still be covered at account and holder level (REQ-BANK-006).
    for (BankCounterLeg leg : legs) {
      BigDecimal negated = leg.amount().negate();
      if (negated.signum() < 0) {
        BankAccount account = lockedAccounts.get(leg.accountId());
        BigDecimal removal = negated.negate();
        BigDecimal holderSub = postingRepository.holderSubBalance(leg.accountId(), leg.holderId());
        if (holderSub.compareTo(removal) < 0) {
          throw holderOverdraft(account.getAccountNo(), leg.holderHandle(), holderSub);
        }
        BigDecimal balance = postingRepository.accountBalance(leg.accountId());
        if (balance.compareTo(removal) < 0) {
          throw accountOverdraft(account.getAccountNo(), balance);
        }
      }
    }

    Instant now = Instant.now();
    BankTransaction reversal =
        persistTransaction(BankTransactionType.REVERSAL, note, original, now);
    for (BankCounterLeg leg : legs) {
      BankHolder holder = requireHolder(leg.holderId());
      persistPosting(
          reversal, lockedAccounts.get(leg.accountId()), holder, leg.amount().negate(), now);
    }
    bankAuditService.record(
        BankAuditEventType.TRANSACTION_REVERSED,
        legs.isEmpty() ? null : legs.getFirst().accountId(),
        reversal.getId(),
        null,
        "reversed " + original.getType() + " " + shortId(original.getId()));
    return toDto(reversal);
  }

  /**
   * Executes the admin wipe reset (REQ-BANK-013): for every account with a non-zero balance one
   * {@code WIPE_RESET} transaction with one negative posting per non-zero (account, holder)
   * sub-balance, bringing balance and every sub-balance to exactly zero. History, statements and
   * audit trail are preserved — nothing is deleted. Idempotent: on an all-zero bank nothing is
   * booked and the result reports zero accounts.
   *
   * @return counts and total for the admin notice; one summarizing audit event is written when
   *     anything was zeroed
   */
  @Transactional
  public BankWipeResetResultDto resetAllBalances() {
    List<BankAccount> accounts = accountRepository.findAllForUpdateOrderById();
    int accountsReset = 0;
    int stashesZeroed = 0;
    BigDecimal totalZeroed = BigDecimal.ZERO;
    Instant now = Instant.now();

    for (BankAccount account : accounts) {
      List<BankHolderBalance> distribution =
          postingRepository.holderDistribution(account.getId()).stream()
              .filter(h -> h.amount().signum() != 0)
              .toList();
      if (distribution.isEmpty()) {
        continue;
      }
      BankTransaction tx =
          persistTransaction(BankTransactionType.WIPE_RESET, "SC wipe reset", null, now);
      for (BankHolderBalance slice : distribution) {
        BankHolder holder = requireHolder(slice.holderId());
        persistPosting(tx, account, holder, slice.amount().negate(), now);
        stashesZeroed++;
        totalZeroed = totalZeroed.add(slice.amount());
      }
      accountsReset++;
    }

    if (accountsReset > 0) {
      bankAuditService.record(
          BankAuditEventType.WIPE_RESET_EXECUTED,
          null,
          null,
          null,
          "accounts="
              + accountsReset
              + ", stashes="
              + stashesZeroed
              + ", totalZeroed="
              + totalZeroed.toPlainString());
    }
    log.info(
        "Bank wipe reset executed: accounts={}, stashes={}, totalZeroed={}",
        accountsReset,
        stashesZeroed,
        totalZeroed);
    return new BankWipeResetResultDto(accountsReset, stashesZeroed, totalZeroed);
  }

  /**
   * Locks one account row for the surrounding transaction (the serialization point of every
   * booking).
   *
   * @param accountId the account to lock
   * @return the locked, managed account
   * @throws NotFoundException when the account does not exist
   */
  private BankAccount lockAccount(@NotNull UUID accountId) {
    return accountRepository
        .findByIdForUpdate(accountId)
        .orElseThrow(() -> new NotFoundException("Bank account not found"));
  }

  /**
   * Rejects bookings on closed accounts (REQ-BANK-002).
   *
   * @param account the locked account
   */
  private void requireActive(@NotNull BankAccount account) {
    if (account.getStatus() != BankAccountStatus.ACTIVE) {
      throw new BankConflictException(
          BankConflictException.CODE_BANK_ACCOUNT_CLOSED,
          "The account is closed and rejects postings",
          Map.of("accountNo", account.getAccountNo()));
    }
  }

  /**
   * Resolves a holder or fails with 404.
   *
   * @param holderId the holder id
   * @return the holder entity
   */
  private BankHolder requireHolder(@NotNull UUID holderId) {
    return holderRepository
        .findById(holderId)
        .orElseThrow(() -> new NotFoundException("Bank holder not found"));
  }

  /**
   * Rejects incoming postings naming a deactivated holder (REQ-BANK-003) — money may still be moved
   * OUT of a deactivated holder's stash.
   *
   * @param holder the receiving holder
   */
  private void requireActiveHolder(@NotNull BankHolder holder) {
    if (!holder.isActive()) {
      throw new BankConflictException(
          BankConflictException.CODE_BANK_HOLDER_INACTIVE,
          "The holder is deactivated and accepts no new money",
          Map.of("holderHandle", holder.getHandle()));
    }
  }

  /**
   * The no-overdraft guard (REQ-BANK-006): the named holder's sub-balance — and, defensively, the
   * account balance — must cover the removal. Runs while the account row is locked, so concurrent
   * bookings cannot jointly overdraw.
   *
   * @param account the locked source account
   * @param holder the paying holder
   * @param amount the positive removal amount
   */
  private void requireCoverage(
      @NotNull BankAccount account, @NotNull BankHolder holder, @NotNull BigDecimal amount) {
    BigDecimal holderSub = postingRepository.holderSubBalance(account.getId(), holder.getId());
    if (holderSub.compareTo(amount) < 0) {
      throw holderOverdraft(account.getAccountNo(), holder.getHandle(), holderSub);
    }
    BigDecimal balance = postingRepository.accountBalance(account.getId());
    if (balance.compareTo(amount) < 0) {
      throw accountOverdraft(account.getAccountNo(), balance);
    }
  }

  /**
   * Builds the account-level overdraft conflict naming account and available balance (REQ-BANK-006
   * acceptance) as structured properties.
   *
   * @param accountNo the account's display number
   * @param available the current balance
   * @return the 409 conflict to throw
   */
  private BankConflictException accountOverdraft(
      @NotNull String accountNo, @NotNull BigDecimal available) {
    return new BankConflictException(
        BankConflictException.CODE_BANK_OVERDRAFT,
        "The booking would overdraw the account",
        Map.of("accountNo", accountNo, "available", plain(available)));
  }

  /**
   * Builds the holder-level overdraft conflict naming account, holder and the holder's available
   * sub-balance (REQ-BANK-006 acceptance) as structured properties.
   *
   * @param accountNo the account's display number
   * @param holderHandle the named holder's handle
   * @param available the holder's current sub-balance on the account
   * @return the 409 conflict to throw
   */
  private BankConflictException holderOverdraft(
      @NotNull String accountNo, @NotNull String holderHandle, @NotNull BigDecimal available) {
    return new BankConflictException(
        BankConflictException.CODE_BANK_HOLDER_OVERDRAFT,
        "The booking exceeds the holder's sub-balance on the account",
        Map.of(
            "accountNo", accountNo,
            "holderHandle", holderHandle,
            "available", plain(available)));
  }

  /**
   * Renders a ledger sum for client display: {@code NUMERIC(19,4)} sums come back as {@code
   * 300.0000} — bank amounts are whole aUEC, so trailing zeros are stripped (plain notation, no
   * scientific rendering).
   *
   * @param amount the sum to render
   * @return the plain whole-number string
   */
  private static String plain(@NotNull BigDecimal amount) {
    return amount.stripTrailingZeros().toPlainString();
  }

  /**
   * Persists one transaction header stamped with the caller and the shared booking instant.
   *
   * @param type the transaction type
   * @param note optional free-text note
   * @param reversed the reversed original for {@code REVERSAL} rows, else {@code null}
   * @param now the shared booking instant
   * @return the persisted header
   */
  private BankTransaction persistTransaction(
      @NotNull BankTransactionType type,
      @Nullable String note,
      @Nullable BankTransaction reversed,
      @NotNull Instant now) {
    BankTransaction tx =
        BankTransaction.builder()
            .type(type)
            .initiatedBy(authHelperService.currentUserId().orElse(null))
            .note(note)
            .reversedTransaction(reversed)
            .createdAt(now)
            .build();
    return transactionRepository.save(tx);
  }

  /**
   * Persists one signed ledger leg stamped with the shared booking instant.
   *
   * @param tx the owning header
   * @param account the posted account
   * @param holder the named holder
   * @param amount the signed amount (never zero — callers always pass validated non-zero values)
   * @param now the shared booking instant
   */
  private void persistPosting(
      @NotNull BankTransaction tx,
      @NotNull BankAccount account,
      @NotNull BankHolder holder,
      @NotNull BigDecimal amount,
      @NotNull Instant now) {
    BankPosting posting =
        BankPosting.builder()
            .transaction(tx)
            .account(account)
            .holder(holder)
            .amount(amount)
            .createdAt(now)
            .build();
    postingRepository.save(posting);
  }

  /**
   * Maps a persisted header to the acknowledgement DTO.
   *
   * @param tx the persisted header
   * @return the acknowledgement
   */
  private static BankTransactionDto toDto(@NotNull BankTransaction tx) {
    return new BankTransactionDto(tx.getId(), tx.getType(), tx.getNote(), tx.getCreatedAt());
  }

  /**
   * Shortens a transaction id to the first hex group for compact audit details (the A2 mockup's "TX
   * a4f1" style).
   *
   * @param id the transaction id
   * @return the first id segment
   */
  private static String shortId(@NotNull UUID id) {
    String s = id.toString();
    return s.substring(0, s.indexOf('-'));
  }
}
