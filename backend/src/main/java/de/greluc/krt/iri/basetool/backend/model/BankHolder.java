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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

/**
 * A player acting as physical custodian of bank money (epic #556, REQ-BANK-003), persisted in the
 * {@code bank_holder} table created by Flyway V151.
 *
 * <p>aUEC exists only on Star Citizen player accounts, so every {@link BankPosting} names exactly
 * one holder — the player whose stash physically changes. The registry row links to the basetool
 * {@link User} (one holder per user, V151 unique constraint) but additionally snapshots the {@link
 * #handle} so the ledger stays readable after user deletion ({@code ON DELETE SET NULL}).
 *
 * <p>Holders are never hard-deleted while postings reference them (V153 {@code ON DELETE
 * RESTRICT}); {@code active = false} blocks new postings naming the holder without touching
 * history.
 */
@Entity
@Table(name = "bank_holder")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class BankHolder extends AbstractEntity<UUID> {

  /** Surrogate primary key, generated client-side by Hibernate ({@code GenerationType.UUID}). */
  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /**
   * The linked basetool user; {@code null} after the user was deleted (the database sets the column
   * to NULL, the {@link #handle} snapshot keeps the row meaningful). Lazy-fetched so listing
   * holders does not hydrate user rows.
   */
  @Nullable
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  @ToString.Exclude
  private User user;

  /**
   * Denormalized player handle captured at registration time (the user's effective name). Shown
   * everywhere the holder appears — ledger rows, distributions, statements — and authoritative once
   * the linked user is gone.
   */
  @Column(nullable = false)
  private String handle;

  /**
   * {@code false} blocks new postings naming this holder (registration mistakes, players leaving
   * custody duty); existing ledger history is never touched. Deactivation requires no zero
   * sub-balance — the money record stays where it physically is.
   */
  @Column(nullable = false)
  private boolean active = true;
}
