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

import de.greluc.krt.iri.basetool.backend.model.BankAuditEvent;
import de.greluc.krt.iri.basetool.backend.model.BankAuditEventType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for the insert-only bank audit trail (epic #556, REQ-BANK-012). Strictly
 * insert-and-read: audit rows are never updated, deleted or retention-swept — the trail is the
 * point. Read access is admin-only and enforced at the controller/URL layer, not here.
 */
@Repository
public interface BankAuditEventRepository extends JpaRepository<BankAuditEvent, UUID> {

  /**
   * One filtered page of the audit log for the admin viewer (A2 mockup): every filter is optional,
   * combinable, and applied via the {@code (:param IS NULL OR ...)} pattern established by the
   * inventory queries.
   *
   * @param from period start (inclusive), or {@code null}
   * @param to period end (inclusive), or {@code null}
   * @param actorUserId filter on the acting user, or {@code null}
   * @param accountId filter on the affected account, or {@code null}
   * @param eventType filter on the event type, or {@code null}
   * @param pageable page, size and whitelisted sort (default {@code occurredAt} descending)
   * @return one page of audit events
   */
  @Query(
      "SELECT e FROM BankAuditEvent e WHERE"
          + " (CAST(:from AS timestamp) IS NULL OR e.occurredAt >= :from)"
          + " AND (CAST(:to AS timestamp) IS NULL OR e.occurredAt <= :to)"
          + " AND (CAST(:actorUserId AS uuid) IS NULL OR e.actorUserId = :actorUserId)"
          + " AND (CAST(:accountId AS uuid) IS NULL OR e.accountId = :accountId)"
          + " AND (CAST(:eventType AS string) IS NULL OR e.eventType = :eventType)")
  Page<BankAuditEvent> findFiltered(
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("actorUserId") UUID actorUserId,
      @Param("accountId") UUID accountId,
      @Param("eventType") BankAuditEventType eventType,
      Pageable pageable);

  /**
   * Probes whether a transaction has its audit row — a targeted helper for tests and callers. The
   * scheduled integrity sweep uses the set-based {@code
   * BankTransactionRepository#findTransactionsWithoutAuditEvent()} instead (REQ-BANK-012/-020).
   *
   * @param transactionId the transaction id
   * @return {@code true} when an audit event references the transaction
   */
  boolean existsByTransactionId(UUID transactionId);
}
