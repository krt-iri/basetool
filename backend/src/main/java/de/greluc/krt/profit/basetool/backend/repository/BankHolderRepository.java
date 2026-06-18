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

package de.greluc.krt.profit.basetool.backend.repository;

import de.greluc.krt.profit.basetool.backend.model.BankHolder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for the bank-local holder registry (epic #556, REQ-BANK-003). Holders are
 * never hard-deleted (the ledger references them with {@code ON DELETE RESTRICT}); the registry
 * list is unbounded by design — it holds one row per custodian player, a few dozen at org scale.
 */
@Repository
public interface BankHolderRepository extends JpaRepository<BankHolder, UUID> {

  /**
   * Looks up the holder row linked to a basetool user — the registration pre-check (one holder per
   * user, V151 unique constraint) and the user-to-holder resolution in the booking flows.
   *
   * @param userId the linked user's id
   * @return the holder row, or empty when the user is not registered as holder
   */
  Optional<BankHolder> findByUserId(UUID userId);

  /**
   * Existence probe for the duplicate-registration pre-check — a clean 409 before the V151 unique
   * constraint would reject the insert.
   *
   * @param userId the linked user's id
   * @return {@code true} when the user already has a holder row
   */
  boolean existsByUserId(UUID userId);

  /**
   * The full registry ordered by handle for the management "Halter" tab (W1 mockup).
   *
   * @return every holder row, ordered by handle
   */
  List<BankHolder> findAllByOrderByHandleAsc();
}
