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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

/**
 * A confirm-before-post deposit/withdrawal request raised by an org-unit officer or lead against
 * their org unit's bank account (epic #666 F2, REQ-BANK-022/-023), persisted in the {@code
 * bank_booking_request} table (Flyway V159).
 *
 * <p>This is the deliberate counterpart to the append-only ledger: unlike {@link BankTransaction} /
 * {@link BankPosting}, a booking request is a <strong>mutable</strong> off-ledger aggregate (it
 * carries the optimistic-locking {@code @Version} from {@link AbstractEntity}) and moves no money
 * while {@code PENDING} (ADR-0021). Only when a bank employee confirms it does the request name the
 * {@link #holder} (the player who received the deposit / paid the withdrawal out), book a real
 * {@link BankTransaction} through {@link BankAccount}'s ledger, link that transaction in {@link
 * #resultingTransaction}, and flip to {@code CONFIRMED}. A {@code REJECTED} / {@code CANCELLED} /
 * {@code PENDING} request never carries a holder or resulting transaction — V159's {@code
 * chk_bank_booking_request_confirmed_refs} constraint pins that invariant.
 *
 * <p>The requester and decider are stored as plain {@code app_user} ids ({@code ON DELETE SET
 * NULL}) plus a denormalized effective-name snapshot, mirroring {@link BankAuditEvent} so the row —
 * and the bank-staff queue rendered from it — survives a user deletion.
 */
@Entity
@Table(name = "bank_booking_request")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class BankBookingRequest extends AbstractEntity<UUID> {

  /** Surrogate primary key, generated client-side by Hibernate ({@code GenerationType.UUID}). */
  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** The org-unit account the request targets; immutable. Always an {@code ORG_UNIT} account. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false, updatable = false)
  @ToString.Exclude
  private BankAccount account;

  /** Whether the request is a deposit or a withdrawal; immutable. */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private BankBookingRequestType type;

  /** The requested whole-aUEC amount, strictly positive; immutable. */
  @Column(nullable = false, precision = 19, scale = 4, updatable = false)
  private BigDecimal amount;

  /**
   * Optional free-text note supplied by the requester; carried onto the booking on confirmation.
   */
  @Nullable
  @Column(length = 500, updatable = false)
  private String note;

  /** Lifecycle state; starts {@code PENDING} and reaches exactly one terminal state. */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private BankBookingRequestStatus status = BankBookingRequestStatus.PENDING;

  /**
   * The requesting officer/lead's {@code app_user} id (JWT {@code sub}); plain UUID with an {@code
   * ON DELETE SET NULL} FK so the request outlives the requester. Drives per-user isolation of the
   * "my requests" list and the cancel-own-request check (REQ-BANK-022).
   */
  @Nullable
  @Column(name = "requested_by", updatable = false)
  private UUID requestedBy;

  /** Denormalized effective-name snapshot of the requester; authoritative after user deletion. */
  @Column(name = "requester_handle", nullable = false, updatable = false)
  private String requesterHandle;

  /**
   * The holder recorded by the bank employee at confirmation (deposit → the player who received the
   * money; withdrawal → the player who paid it out); {@code null} until {@code CONFIRMED}.
   */
  @Nullable
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "holder_id")
  @ToString.Exclude
  private BankHolder holder;

  /** The ledger transaction booked on confirmation; {@code null} until {@code CONFIRMED}. */
  @Nullable
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "resulting_transaction_id")
  @ToString.Exclude
  private BankTransaction resultingTransaction;

  /**
   * The deciding bank employee's {@code app_user} id; {@code null} for pending/cancelled requests.
   */
  @Nullable
  @Column(name = "decided_by")
  private UUID decidedBy;

  /**
   * Denormalized effective-name snapshot of the deciding bank employee; {@code null} until decided.
   */
  @Nullable
  @Column(name = "decider_handle")
  private String deciderHandle;

  /**
   * When the request reached its terminal state (confirmed/rejected/cancelled); {@code null} while
   * pending.
   */
  @Nullable
  @Column(name = "decided_at")
  private Instant decidedAt;

  /** The bank employee's reason when the request was {@code REJECTED}; {@code null} otherwise. */
  @Nullable
  @Column(name = "reject_reason", length = 500)
  private String rejectReason;
}
