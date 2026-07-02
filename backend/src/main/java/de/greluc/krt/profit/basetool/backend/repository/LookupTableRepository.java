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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Shared base for lookup-table repositories that enforce a case-insensitive-unique {@code name}
 * column (S5, #911): every implementor's admin create/rename flow needs to surface a 409 Conflict
 * before the SQL UNIQUE constraint trips, so it pre-checks with {@link
 * #existsByNameIgnoreCase(String)} (create) or {@link #existsByNameIgnoreCaseAndIdNot(String,
 * Object)} (rename — excludes the row being renamed so keeping the existing name is not a
 * self-collision).
 *
 * <p>{@code @NoRepositoryBean} — Spring Data does not instantiate this interface directly; each
 * extending repository (e.g. {@code SquadronRepository extends LookupTableRepository<Squadron,
 * UUID>}) gets its own proxy, and Spring Data resolves {@link #existsByNameIgnoreCase(String)} /
 * {@link #existsByNameIgnoreCaseAndIdNot(String, Object)} as a derived query against <em>that</em>
 * entity's {@code name} column — the method declarations are shared, the generated SQL is not.
 *
 * <p><b>Why only the exists-pair, not {@code findByNameIgnoreCase} too.</b> The finder's return
 * type diverges across implementors: most lookup tables have a unique {@code name} and return
 * {@code Optional<T>}, but {@code GameItem.name} is not unique (multiple items can share a display
 * name), so {@code GameItemRepository.findByNameIgnoreCase} returns {@code List<GameItem>}. A base
 * method can only declare one return type, so the finder stays declared per-repository; only the
 * boolean exists-pair — whose return type never varies — lifts cleanly into this base.
 *
 * @param <T> the entity type, which must expose a case-insensitive-unique {@code name} column
 * @param <IdT> the entity's identifier type
 */
@NoRepositoryBean
public interface LookupTableRepository<T, IdT> extends JpaRepository<T, IdT> {

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * NameIgnoreCase}.
   *
   * @param name the proposed name; never {@code null}
   * @return {@code true} iff at least one row already carries this name (case-insensitive)
   */
  boolean existsByNameIgnoreCase(String name);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * NameIgnoreCaseAndIdNot}.
   *
   * @param name the proposed name; never {@code null}
   * @param id the id of the row currently being renamed; never {@code null}
   * @return {@code true} iff at least one OTHER row already carries this name (case-insensitive)
   */
  boolean existsByNameIgnoreCaseAndIdNot(String name, IdT id);
}
