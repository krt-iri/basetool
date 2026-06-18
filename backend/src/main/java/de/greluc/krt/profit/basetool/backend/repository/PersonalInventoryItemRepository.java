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

import de.greluc.krt.profit.basetool.backend.model.PersonalInventoryItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link PersonalInventoryItem}. All non-admin lookups MUST use one of
 * the {@code *ByOwnerSub*} variants in order to enforce the multi-user data isolation rule (see
 * AGENTS.md "MULTI-USER DATA ISOLATION").
 */
@Repository
public interface PersonalInventoryItemRepository
    extends JpaRepository<PersonalInventoryItem, UUID> {

  /** Returns every entity matching the derived {@code findAllByOwnerSub} criteria. */
  Page<PersonalInventoryItem> findAllByOwnerSub(String ownerSub, Pageable pageable);

  /**
   * Returns every entity matching the derived {@code findAllByOwnerSubAndNameContainingIgnoreCase}
   * criteria.
   */
  Page<PersonalInventoryItem> findAllByOwnerSubAndNameContainingIgnoreCase(
      String ownerSub, String nameFragment, Pageable pageable);

  /** Derived Spring-Data query - returns entities matching {@code IdAndOwnerSub}. */
  Optional<PersonalInventoryItem> findByIdAndOwnerSub(UUID id, String ownerSub);
}
