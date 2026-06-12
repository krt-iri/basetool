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

package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One signed leg of a {@link BankTransaction} (epic #556, REQ-BANK-004, ADR-0010), persisted in the
 * {@code bank_posting} table created by Flyway V153.
 *
 * <p>Insert-only like its header — no {@code @Version}, never updated or deleted. The account
 * balance is {@code SUM(amount)} over its postings; the holder distribution is the same sum grouped
 * by {@link #holder} (REQ-BANK-003). Every leg names exactly one holder: the player whose physical
 * stash the leg changes. Amounts are signed whole-aUEC {@code NUMERIC(19,4)} values (ADR-0002) and
 * never zero (V153 CHECK).
 */
@Entity
@Table(name = "bank_posting")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankPosting {

  /** Surrogate primary key, generated client-side by Hibernate ({@code GenerationType.UUID}). */
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** The transaction header this leg belongs to; lazy — aggregates query by plain columns. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
  @ToString.Exclude
  private BankTransaction transaction;

  /** The account whose balance this leg changes; lazy for the same reason as the header. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false, updatable = false)
  @ToString.Exclude
  private BankAccount account;

  /**
   * The player physically holding/moving the money (REQ-BANK-003); mandatory on every leg. The
   * database restricts holder deletion while postings reference it ({@code ON DELETE RESTRICT}).
   */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "holder_id", nullable = false, updatable = false)
  @ToString.Exclude
  private BankHolder holder;

  /**
   * Signed whole-aUEC amount ({@code NUMERIC(19,4)}, ADR-0002): positive legs add to the
   * account/holder stash, negative legs remove from it; never zero (V153 CHECK).
   */
  @Column(nullable = false, precision = 19, scale = 4, updatable = false)
  private BigDecimal amount;

  /**
   * Booking instant (UTC), stamped by {@code BankLedgerService} with the exact same value as the
   * owning transaction's {@link BankTransaction#getCreatedAt()} so range queries never split a
   * transaction. Backs the {@code (account_id, created_at)} index (REQ-BANK-020).
   */
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
