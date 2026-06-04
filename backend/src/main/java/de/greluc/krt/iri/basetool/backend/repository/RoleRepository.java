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

import de.greluc.krt.iri.basetool.backend.model.Role;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Role. */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
  /**
   * Derived Spring-Data query - returns entities matching {@code Name}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"permissions"})
  Optional<Role> findByName(String name);

  /**
   * Derived Spring-Data query - returns entities matching {@code NameIgnoreCase}. Eagerly fetches
   * the configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"permissions"})
  Optional<Role> findByNameIgnoreCase(String name);

  /**
   * Derived Spring-Data query - returns entities matching {@code Code}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"permissions"})
  Optional<Role> findByCode(String code);

  /**
   * Lists every entity. Overridden here to attach an {@code @EntityGraph}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @Override
  @NotNull
  @EntityGraph(attributePaths = {"permissions"})
  Page<Role> findAll(@NotNull Pageable pageable);
}
