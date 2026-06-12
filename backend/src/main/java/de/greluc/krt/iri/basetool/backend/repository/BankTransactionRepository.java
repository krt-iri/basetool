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

import de.greluc.krt.iri.basetool.backend.model.BankTransaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for the append-only {@link BankTransaction} headers (epic #556, ADR-0010).
 * Strictly insert-and-read: no {@code @Modifying} method may ever appear here — the ledger is never
 * updated or deleted (REQ-BANK-004, pinned by {@code ArchitectureTest}).
 */
@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {

  /**
   * Probes whether a transaction has already been reversed — the pre-check behind the stable 409
   * code {@code BANK_ALREADY_REVERSED} (the V153 unique constraint is the backstop).
   *
   * @param reversedTransactionId the original transaction's id
   * @return {@code true} when a {@code REVERSAL} referencing it exists
   */
  boolean existsByReversedTransactionId(UUID reversedTransactionId);

  /**
   * Integrity check (REQ-BANK-020): ids of {@code TRANSFER} transactions whose postings do not sum
   * to zero — a balanced double-entry transfer (incl. an intra-account holder rebooking) must net
   * to zero across its legs.
   *
   * @return the violating transfer transaction ids (empty when sound)
   */
  @Query(
      "SELECT t.id FROM BankPosting p JOIN p.transaction t WHERE t.type ="
          + " de.greluc.krt.iri.basetool.backend.model.BankTransactionType.TRANSFER"
          + " GROUP BY t.id HAVING SUM(p.amount) <> 0")
  List<UUID> findTransferTransactionsWithNonZeroSum();

  /**
   * Integrity check (REQ-BANK-020): ids of {@code REVERSAL} transactions whose combined postings
   * with the reversed transaction do not cancel per {@code (account, holder)} — a reversal must be
   * the negated mirror of the original (ADR-0010), so the union of both transactions' postings nets
   * to zero for every account/holder pair they touch.
   *
   * @return the violating reversal transaction ids (empty when sound)
   */
  @Query(
      value =
          "SELECT rt.id FROM bank_transaction rt"
              + " WHERE rt.type = 'REVERSAL' AND EXISTS ("
              + "   SELECT 1 FROM bank_posting p"
              + "   WHERE p.transaction_id IN (rt.id, rt.reversed_transaction_id)"
              + "   GROUP BY p.account_id, p.holder_id HAVING SUM(p.amount) <> 0)",
      nativeQuery = true)
  List<UUID> findReversalTransactionsNotMirrored();
}
