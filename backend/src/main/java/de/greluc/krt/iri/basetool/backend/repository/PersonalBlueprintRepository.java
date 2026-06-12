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

import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link PersonalBlueprint}. All non-admin lookups MUST use one of the
 * {@code *ByOwnerSub*} variants to enforce the multi-user data isolation rule: a user only ever
 * sees blueprints they own.
 */
@Repository
public interface PersonalBlueprintRepository extends JpaRepository<PersonalBlueprint, UUID> {

  /**
   * Page of the blueprints owned by one user.
   *
   * @param ownerSub Keycloak {@code sub} of the owner
   * @param pageable page request with a whitelisted sort
   * @return the owner's blueprints
   */
  Page<PersonalBlueprint> findAllByOwnerSub(String ownerSub, Pageable pageable);

  /**
   * Page of the blueprints owned by one user whose product name contains the given fragment
   * (case-insensitive) — backs the owned-list filter box.
   *
   * @param ownerSub Keycloak {@code sub} of the owner
   * @param nameFragment case-insensitive product-name substring
   * @param pageable page request with a whitelisted sort
   * @return the owner's matching blueprints
   */
  Page<PersonalBlueprint> findAllByOwnerSubAndProductNameContainingIgnoreCase(
      String ownerSub, String nameFragment, Pageable pageable);

  /**
   * Owner-scoped single lookup for detail / update / delete; returns empty for a foreign or unknown
   * id so the service can answer 404 without leaking another user's ownership.
   *
   * @param id the entry id
   * @param ownerSub Keycloak {@code sub} of the owner
   * @return the entry if it belongs to the owner, empty otherwise
   */
  Optional<PersonalBlueprint> findByIdAndOwnerSub(UUID id, String ownerSub);

  /**
   * Owner-scoped product lookup; used by add / import to detect an existing ownership row before
   * the {@code (owner_sub, product_key)} unique constraint fires.
   *
   * @param ownerSub Keycloak {@code sub} of the owner
   * @param productKey normalized product key
   * @return the entry if the owner already owns the product, empty otherwise
   */
  Optional<PersonalBlueprint> findByOwnerSubAndProductKey(String ownerSub, String productKey);

  /**
   * Fast owner-scoped existence check for the product, used to short-circuit duplicate adds with a
   * 409 before hitting the unique constraint.
   *
   * @param ownerSub Keycloak {@code sub} of the owner
   * @param productKey normalized product key
   * @return {@code true} if the owner already owns the product
   */
  boolean existsByOwnerSubAndProductKey(String ownerSub, String productKey);

  /**
   * Owner-scoped bulk product lookup, used to compute the "already owned" flag for a page of search
   * results and to dedupe a batch add / import in a single query.
   *
   * @param ownerSub Keycloak {@code sub} of the owner
   * @param productKeys the product keys to test
   * @return the owner's entries whose product key is in the given set
   */
  List<PersonalBlueprint> findAllByOwnerSubAndProductKeyIn(
      String ownerSub, Collection<String> productKeys);

  /**
   * Bulk owner lookup across several owners — backs the org-unit blueprint availability aggregation
   * (#364): given the Keycloak {@code sub}s of every in-scope org-unit member, returns all their
   * owned-blueprint rows for grouping by product in the service layer.
   *
   * @param ownerSubs the Keycloak {@code sub}s of the in-scope owners
   * @return every owned-blueprint row whose owner is in the given set; never {@code null}
   */
  List<PersonalBlueprint> findAllByOwnerSubIn(Collection<String> ownerSubs);

  /**
   * Owner-restricted product lookup — backs the availability drill-down (#364): given one product
   * key and the Keycloak {@code sub}s of every in-scope member, returns the rows that pin which of
   * those members own the product.
   *
   * @param productKey the normalized product key to match
   * @param ownerSubs the Keycloak {@code sub}s of the in-scope owners
   * @return the matching rows (one per owning in-scope member); never {@code null}
   */
  List<PersonalBlueprint> findAllByProductKeyAndOwnerSubIn(
      String productKey, Collection<String> ownerSubs);

  /**
   * Unrestricted product lookup — backs the admin "all org units" branch of the availability
   * drill-down (#364). That scope spans every blueprint owner anyway, so enumerating all distinct
   * {@code owner_sub}s first and echoing them back as an {@code IN} list (the previous
   * implementation) only added a full-table scan plus an unbounded parameter list to every expand
   * click. ADMIN-ONLY: every scoped caller must keep using {@link
   * #findAllByProductKeyAndOwnerSubIn(String, Collection)} so the owner-isolation rule holds.
   *
   * @param productKey the normalized product key to match
   * @return every owned-blueprint row for the product, across all owners; never {@code null}
   */
  List<PersonalBlueprint> findAllByProductKey(String productKey);

  /**
   * Bulk owner + product lookup — backs the item job-order blueprint-coverage view: given the
   * Keycloak {@code sub}s of every member of the order's responsible org unit and the set of
   * normalized product keys the order's item lines resolve to, returns exactly the owned-blueprint
   * rows that match both, so the service can group them by owner and by product in one query.
   *
   * @param ownerSubs the Keycloak {@code sub}s of the responsible org unit's members
   * @param productKeys the normalized product keys of the order's required items
   * @return the matching rows (one per owning member × owned required product); never {@code null}
   */
  List<PersonalBlueprint> findAllByOwnerSubInAndProductKeyIn(
      Collection<String> ownerSubs, Collection<String> productKeys);

  /**
   * Returns the distinct Keycloak {@code sub} of every blueprint owner in the table. Backs the
   * admin "all org units" branch of the availability overview (#364, #371 fix): that scope spans
   * every owner — including a user with no org-unit membership (e.g. an admin without a Staffel) —
   * which a membership-derived member list silently dropped. The owned rows are still fetched
   * through {@link #findAllByOwnerSubIn(Collection)}, so the owner-isolation contract is unchanged.
   *
   * @return every distinct {@code owner_sub} present in the table; never {@code null}, possibly
   *     empty.
   */
  @Query("SELECT DISTINCT pb.ownerSub FROM PersonalBlueprint pb")
  Set<String> findAllDistinctOwnerSubs();
}
