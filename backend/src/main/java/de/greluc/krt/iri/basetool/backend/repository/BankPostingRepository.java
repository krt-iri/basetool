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

package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.BankPosting;
import de.greluc.krt.iri.basetool.backend.model.projection.BankAccountBalance;
import de.greluc.krt.iri.basetool.backend.model.projection.BankBookingRow;
import de.greluc.krt.iri.basetool.backend.model.projection.BankCounterLeg;
import de.greluc.krt.iri.basetool.backend.model.projection.BankHolderBalance;
import de.greluc.krt.iri.basetool.backend.model.projection.BankPostingSlice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for the append-only {@link BankPosting} ledger legs (epic #556, ADR-0010).
 * Strictly insert-and-read: no {@code @Modifying} method may ever appear here — the ledger is never
 * updated or deleted (REQ-BANK-004, pinned by {@code ArchitectureTest}). All balances are computed
 * here as grouped sums (compute-on-read, REQ-BANK-020), backed by the V153 composite indexes.
 */
@Repository
public interface BankPostingRepository extends JpaRepository<BankPosting, UUID> {

  /**
   * The account balance: signed sum of all posting amounts (ADR-0010). Zero for an account without
   * postings.
   *
   * @param accountId the account
   * @return the current balance, never {@code null}
   */
  @Query("SELECT COALESCE(SUM(p.amount), 0) FROM BankPosting p WHERE p.account.id = :accountId")
  BigDecimal accountBalance(@Param("accountId") UUID accountId);

  /**
   * One holder's sub-balance on one account — the per-holder no-overdraft guard input
   * (REQ-BANK-006). Runs inside the booking transaction while the account row is pessimistically
   * locked, so concurrent bookings cannot jointly overdraw the stash.
   *
   * @param accountId the account
   * @param holderId the holder
   * @return the holder's current sub-balance on the account, never {@code null}
   */
  @Query(
      "SELECT COALESCE(SUM(p.amount), 0) FROM BankPosting p"
          + " WHERE p.account.id = :accountId AND p.holder.id = :holderId")
  BigDecimal holderSubBalance(@Param("accountId") UUID accountId, @Param("holderId") UUID holderId);

  /**
   * The account's holder distribution (REQ-BANK-003): per-holder sub-balances as one grouped
   * statement, largest stash first. Includes zero rows (a holder who once held money and was
   * emptied) so the service can filter as the surface requires.
   *
   * @param accountId the account
   * @return per-holder sub-balances, ordered by amount descending
   */
  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.projection.BankHolderBalance("
          + "h.id, h.handle, h.active, SUM(p.amount))"
          + " FROM BankPosting p JOIN p.holder h WHERE p.account.id = :accountId"
          + " GROUP BY h.id, h.handle, h.active ORDER BY SUM(p.amount) DESC, h.handle ASC")
  List<BankHolderBalance> holderDistribution(@Param("accountId") UUID accountId);

  /**
   * The holder distribution as of a point in time — the statement's closing distribution
   * (REQ-BANK-014).
   *
   * @param accountId the account
   * @param until inclusive upper bound for the posting timestamps
   * @return per-holder sub-balances at {@code until}, ordered by amount descending
   */
  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.projection.BankHolderBalance("
          + "h.id, h.handle, h.active, SUM(p.amount))"
          + " FROM BankPosting p JOIN p.holder h"
          + " WHERE p.account.id = :accountId AND p.createdAt <= :until"
          + " GROUP BY h.id, h.handle, h.active ORDER BY SUM(p.amount) DESC, h.handle ASC")
  List<BankHolderBalance> holderDistributionUntil(
      @Param("accountId") UUID accountId, @Param("until") Instant until);

  /**
   * Per-holder totals across ALL accounts in one grouped statement — the "Haelt gesamt" column of
   * the holder registry (W1 mockup) without an N+1.
   *
   * @return per-holder totals over the whole bank
   */
  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.projection.BankHolderBalance("
          + "h.id, h.handle, h.active, SUM(p.amount))"
          + " FROM BankPosting p JOIN p.holder h GROUP BY h.id, h.handle, h.active")
  List<BankHolderBalance> holderTotals();

  /**
   * One holder's total custody across ALL accounts — the targeted single-holder form of {@link
   * #holderTotals()} for the holder-update response, avoiding a bank-wide aggregate just to read
   * one holder's total.
   *
   * @param holderId the holder
   * @return the holder's total across the whole bank, never {@code null}
   */
  @Query("SELECT COALESCE(SUM(p.amount), 0) FROM BankPosting p WHERE p.holder.id = :holderId")
  BigDecimal holderTotal(@Param("holderId") UUID holderId);

  /**
   * Per-holder count of accounts with a non-zero sub-balance, batched over all holders — the
   * "Konten" column of the holder registry.
   *
   * @return rows of {@code [holderId, accountCount]}
   */
  @Query(
      "SELECT p.holder.id, COUNT(DISTINCT p.account.id) FROM BankPosting p"
          + " GROUP BY p.holder.id")
  List<Object[]> holderAccountCounts();

  /**
   * Balances of many accounts in ONE grouped statement — the dashboard and the management list
   * (REQ-BANK-016, REQ-DATA-003: no per-account N+1). Accounts without postings produce no row.
   *
   * @param accountIds the accounts to sum
   * @return one balance row per account that has postings
   */
  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.projection.BankAccountBalance("
          + "p.account.id, SUM(p.amount))"
          + " FROM BankPosting p WHERE p.account.id IN :accountIds GROUP BY p.account.id")
  List<BankAccountBalance> accountBalances(@Param("accountIds") Collection<UUID> accountIds);

  /**
   * All postings of the given accounts since a cutoff, reduced to the dashboard-relevant columns in
   * ONE statement; the service derives 30-day deltas, in/out totals and the daily sparkline series
   * in memory (REQ-BANK-016).
   *
   * @param accountIds the visible accounts
   * @param cutoff window start (inclusive)
   * @return the posting slices inside the window
   */
  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.projection.BankPostingSlice("
          + "p.account.id, p.createdAt, p.amount)"
          + " FROM BankPosting p WHERE p.account.id IN :accountIds AND p.createdAt >= :cutoff")
  List<BankPostingSlice> postingSlicesSince(
      @Param("accountIds") Collection<UUID> accountIds, @Param("cutoff") Instant cutoff);

  /**
   * One page of an account's booking history — posting joined with header and holder in a single
   * statement (REQ-BANK-018); newest first by default (whitelisted sort on {@code createdAt}).
   * Intra-account holder rebookings produce two legs on the SAME account; the history collapses the
   * pair onto its negative leg (the K1 mockup renders one "Umbuchung src → dst" row with a neutral
   * amount), so the positive sibling leg is excluded here — statements keep both legs.
   *
   * @param accountId the account
   * @param pageable page, size and whitelisted sort
   * @return one page of booking rows
   */
  @Query(
      value =
          "SELECT new de.greluc.krt.iri.basetool.backend.model.projection.BankBookingRow("
              + "p.id, t.id, t.type, p.amount, h.handle, t.note, p.createdAt, rt.id)"
              + " FROM BankPosting p JOIN p.transaction t JOIN p.holder h"
              + " LEFT JOIN t.reversedTransaction rt WHERE p.account.id = :accountId"
              + " AND NOT (p.amount > 0 AND EXISTS ("
              + "   SELECT p2 FROM BankPosting p2"
              + "   WHERE p2.transaction = p.transaction AND p2.account.id = :accountId"
              + "     AND p2.id <> p.id))",
      countQuery =
          "SELECT COUNT(p) FROM BankPosting p WHERE p.account.id = :accountId"
              + " AND NOT (p.amount > 0 AND EXISTS ("
              + "   SELECT p2 FROM BankPosting p2"
              + "   WHERE p2.transaction = p.transaction AND p2.account.id = :accountId"
              + "     AND p2.id <> p.id))")
  Page<BankBookingRow> findBookings(@Param("accountId") UUID accountId, Pageable pageable);

  /**
   * An account's booking rows inside a period, oldest first — the statement body (REQ-BANK-014).
   *
   * @param accountId the account
   * @param from period start (inclusive)
   * @param to period end (inclusive)
   * @return the period's booking rows in chronological order
   */
  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.projection.BankBookingRow("
          + "p.id, t.id, t.type, p.amount, h.handle, t.note, p.createdAt, rt.id)"
          + " FROM BankPosting p JOIN p.transaction t JOIN p.holder h"
          + " LEFT JOIN t.reversedTransaction rt"
          + " WHERE p.account.id = :accountId AND p.createdAt >= :from AND p.createdAt <= :to"
          + " ORDER BY p.createdAt ASC, p.id ASC")
  List<BankBookingRow> findBookingsInPeriod(
      @Param("accountId") UUID accountId, @Param("from") Instant from, @Param("to") Instant to);

  /**
   * The account balance immediately before an instant — the statement's opening balance
   * (REQ-BANK-014).
   *
   * @param accountId the account
   * @param before exclusive upper bound
   * @return the balance built from all postings strictly before {@code before}, never {@code null}
   */
  @Query(
      "SELECT COALESCE(SUM(p.amount), 0) FROM BankPosting p"
          + " WHERE p.account.id = :accountId AND p.createdAt < :before")
  BigDecimal accountBalanceBefore(
      @Param("accountId") UUID accountId, @Param("before") Instant before);

  /**
   * All legs of the given transactions with account and holder labels, batched in one IN-query —
   * counter-leg resolution for transfer rows and the negated mirror of a reversal (ADR-0010).
   *
   * @param transactionIds the batch of transaction ids
   * @return every leg of the given transactions
   */
  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.projection.BankCounterLeg("
          + "t.id, p.id, a.id, a.accountNo, a.name, h.id, h.handle, p.amount)"
          + " FROM BankPosting p JOIN p.transaction t JOIN p.account a JOIN p.holder h"
          + " WHERE t.id IN :transactionIds")
  List<BankCounterLeg> findLegsByTransactionIds(
      @Param("transactionIds") Collection<UUID> transactionIds);

  /**
   * Number of postings on one account — the detail page's facts strip ("Buchungen").
   *
   * @param accountId the account
   * @return the posting count
   */
  long countByAccountId(UUID accountId);

  /**
   * Number of distinct holders with at least one posting on the account — the facts strip
   * ("Halter").
   *
   * @param accountId the account
   * @return the distinct holder count
   */
  @Query("SELECT COUNT(DISTINCT p.holder.id) FROM BankPosting p WHERE p.account.id = :accountId")
  long countDistinctHoldersByAccountId(@Param("accountId") UUID accountId);

  /**
   * Probes whether a holder appears on any posting — deactivation surfaces and the integrity job.
   *
   * @param holderId the holder
   * @return {@code true} when at least one posting names the holder
   */
  boolean existsByHolderId(UUID holderId);

  /**
   * Integrity check (REQ-BANK-020): ids of accounts whose total balance is negative — an invariant
   * the no-overdraft guard (REQ-BANK-006) should make impossible.
   *
   * @return the violating account ids (empty when sound)
   */
  @Query("SELECT p.account.id FROM BankPosting p GROUP BY p.account.id HAVING SUM(p.amount) < 0")
  List<UUID> findAccountIdsWithNegativeBalance();

  /**
   * Integrity check (REQ-BANK-020): {@code [accountId, holderId]} pairs whose holder sub-balance is
   * negative — the per-holder no-overdraft guard should make this impossible.
   *
   * @return rows of {@code [accountId, holderId]} (empty when sound)
   */
  @Query(
      "SELECT p.account.id, p.holder.id FROM BankPosting p"
          + " GROUP BY p.account.id, p.holder.id HAVING SUM(p.amount) < 0")
  List<Object[]> findAccountHolderPairsWithNegativeBalance();
}
