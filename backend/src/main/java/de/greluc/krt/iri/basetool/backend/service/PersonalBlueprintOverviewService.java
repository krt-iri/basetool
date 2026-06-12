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

package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintOverviewEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintOverviewOwnerDto;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.PersonalBlueprintRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates, for the leadership oversight view (#364), which crafting blueprints are available
 * among the members of the caller's oversight org units, and which members own a given blueprint.
 *
 * <p>{@link PersonalBlueprint} carries no org-unit column — it is a pure per-user aggregate keyed
 * by the Keycloak {@code sub}. This service bridges to org units in two steps, entirely in Java (no
 * cross-type SQL join between the {@code String owner_sub} and the {@code UUID} membership key): it
 * resolves the in-scope member user ids from {@link OrgUnitMembershipRepository} using the
 * oversight {@link ScopePredicate} from {@link OwnerScopeService#currentBlueprintOversightScope()},
 * then loads and groups those members' owned-blueprint rows by product.
 *
 * <p>Owner identity never leaves the service except as a display name in the drill-down — the list
 * view exposes only product + owner count, and the drill-down exposes only {@link
 * de.greluc.krt.iri.basetool.backend.model.User#getEffectiveName()} (never the {@code sub} or
 * e-mail), preserving the project's data-isolation rule.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonalBlueprintOverviewService {

  /** Whitelisted sort fields for the availability list — only the product display name. */
  public static final Set<String> SORTABLE_FIELDS = Set.of("productName");

  /** Default sort field applied when the request supplies none. */
  public static final String DEFAULT_SORT_FIELD = "productName";

  private final OwnerScopeService ownerScopeService;
  private final OrgUnitMembershipRepository orgUnitMembershipRepository;
  private final PersonalBlueprintRepository personalBlueprintRepository;
  private final UserRepository userRepository;

  /**
   * Lists the distinct blueprints available among the members of the caller's oversight org units,
   * one row per product with the count of distinct in-scope members that own it. Returns an empty
   * page when the caller oversees no org unit (the {@link
   * OwnerScopeService#canAccessBlueprintOverview()} gate already keeps non-leadership callers out).
   *
   * @param pageable page request whose sort is restricted to {@link #SORTABLE_FIELDS}
   * @return a page of {@link BlueprintOverviewEntryDto}, sorted by product name then product key
   */
  @NotNull
  public Page<BlueprintOverviewEntryDto> listAvailableBlueprints(@NotNull Pageable pageable) {
    Set<String> ownerSubs = inScopeOwnerSubs();
    if (ownerSubs.isEmpty()) {
      return new PageImpl<>(List.of(), pageable, 0);
    }
    Map<String, ProductAggregate> byKey = new LinkedHashMap<>();
    for (PersonalBlueprint bp : personalBlueprintRepository.findAllByOwnerSubIn(ownerSubs)) {
      byKey
          .computeIfAbsent(bp.getProductKey(), key -> new ProductAggregate(bp.getProductName()))
          .owners
          .add(bp.getOwnerSub());
    }
    List<BlueprintOverviewEntryDto> all =
        byKey.entrySet().stream()
            .map(
                entry ->
                    new BlueprintOverviewEntryDto(
                        entry.getKey(),
                        entry.getValue().productName,
                        entry.getValue().owners.size()))
            .sorted(entryComparator(pageable))
            .toList();
    return paginate(all, pageable);
  }

  /**
   * Lists the in-scope members that own the given product, by display name. Re-resolves the
   * oversight scope server-side so a client cannot widen it through the query parameter. Returns an
   * empty list when the caller oversees no org unit or nobody in scope owns the product.
   *
   * <p>This is the hot path behind every expand click on the availability page, so the admin "all
   * org units" scope queries by product key alone (REQ-INV-012): pre-resolving every distinct
   * {@code owner_sub} just to echo the full set back as an {@code IN} restriction added a
   * table-wide scan plus an unbounded parameter list per click without narrowing anything. Scoped
   * callers keep the owner-restricted lookup so the isolation contract is untouched.
   *
   * @param productKey the normalized product key whose owners to resolve
   * @return the owning in-scope members' display names, sorted case-insensitively; never {@code
   *     null}
   */
  @NotNull
  public List<BlueprintOverviewOwnerDto> listOwnersForProduct(String productKey) {
    ScopePredicate scope = ownerScopeService.currentBlueprintOversightScope();
    List<PersonalBlueprint> owned;
    if (scope.adminAllScope()) {
      owned = personalBlueprintRepository.findAllByProductKey(productKey);
    } else {
      Set<String> ownerSubs = oversightMemberSubs(scope);
      if (ownerSubs.isEmpty()) {
        return List.of();
      }
      owned = personalBlueprintRepository.findAllByProductKeyAndOwnerSubIn(productKey, ownerSubs);
    }
    Set<UUID> ownerIds =
        owned.stream()
            .map(PersonalBlueprint::getOwnerSub)
            .map(PersonalBlueprintOverviewService::parseUuid)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    if (ownerIds.isEmpty()) {
      return List.of();
    }
    return userRepository.findAllById(ownerIds).stream()
        .map(user -> new BlueprintOverviewOwnerDto(user.getEffectiveName()))
        .sorted(
            Comparator.comparing(
                BlueprintOverviewOwnerDto::ownerName, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  /**
   * Resolves the Keycloak {@code sub}s of every user in the caller's oversight scope. For the admin
   * "all org units" scope this is every blueprint owner in the system (via {@link
   * PersonalBlueprintRepository#findAllDistinctOwnerSubs()}) — including owners with no org-unit
   * membership (e.g. a squadron-less admin), which the previous member-list resolution dropped so
   * the admin's own blueprints went missing (#371 fix). For a pinned or member-union scope it is
   * the {@code sub} form of the in-scope org units' member ids (the {@code owner_sub} stored on
   * {@link PersonalBlueprint} equals {@code User.id}).
   *
   * @return the in-scope owner {@code sub}s; empty when a non-admin caller oversees no org unit
   */
  @NotNull
  private Set<String> inScopeOwnerSubs() {
    ScopePredicate scope = ownerScopeService.currentBlueprintOversightScope();
    if (scope.adminAllScope()) {
      return personalBlueprintRepository.findAllDistinctOwnerSubs();
    }
    return oversightMemberSubs(scope);
  }

  /**
   * Resolves the member {@code sub}s of a non-admin oversight scope: the pinned org unit's members
   * when a valid pin is active, otherwise the union over all oversight org units (the {@code
   * owner_sub} stored on {@link PersonalBlueprint} equals {@code User.id}).
   *
   * @param scope the caller's non-admin oversight scope
   * @return the in-scope member {@code sub}s; empty when the caller oversees no org unit
   */
  @NotNull
  private Set<String> oversightMemberSubs(@NotNull ScopePredicate scope) {
    Set<UUID> userIds;
    if (scope.activeOrgUnitId() != null) {
      userIds =
          orgUnitMembershipRepository.findDistinctUserIdsByOrgUnitIdIn(
              Set.of(scope.activeOrgUnitId()));
    } else if (!scope.memberOrgUnitIds().isEmpty()) {
      userIds =
          orgUnitMembershipRepository.findDistinctUserIdsByOrgUnitIdIn(scope.memberOrgUnitIds());
    } else {
      return Set.of();
    }
    return userIds.stream()
        .map(UUID::toString)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Builds the in-memory comparator for the availability list: product name (honouring the request
   * direction, case-insensitive), then product key as a stable tiebreaker.
   *
   * @param pageable the page request carrying the (whitelisted) sort
   * @return the comparator to order the aggregated entries by
   */
  @NotNull
  private static Comparator<BlueprintOverviewEntryDto> entryComparator(@NotNull Pageable pageable) {
    Sort.Order order = pageable.getSort().getOrderFor("productName");
    Comparator<BlueprintOverviewEntryDto> byName =
        Comparator.comparing(BlueprintOverviewEntryDto::productName, String.CASE_INSENSITIVE_ORDER);
    if (order != null && order.isDescending()) {
      byName = byName.reversed();
    }
    return byName.thenComparing(BlueprintOverviewEntryDto::productKey);
  }

  /**
   * Cuts the requested page out of the fully-sorted entry list and wraps it as a {@link Page} so
   * the controller can echo the standard {@code PageResponse} envelope.
   *
   * @param all the complete, sorted entry list
   * @param pageable the page request
   * @return the requested slice as a {@link Page}
   */
  @NotNull
  private static Page<BlueprintOverviewEntryDto> paginate(
      @NotNull List<BlueprintOverviewEntryDto> all, @NotNull Pageable pageable) {
    int total = all.size();
    int from = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), total);
    int to = (int) Math.min((long) from + pageable.getPageSize(), total);
    return new PageImpl<>(List.copyOf(all.subList(from, to)), pageable, total);
  }

  /**
   * Parses a stored {@code owner_sub} back into a {@link UUID}, returning {@code null} for the
   * (theoretical) malformed value so it is filtered out instead of aborting the drill-down.
   *
   * @param raw the {@code owner_sub} string
   * @return the parsed id, or {@code null} when {@code raw} is not a UUID
   */
  @Nullable
  private static UUID parseUuid(String raw) {
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  /**
   * Mutable per-product accumulator used while grouping owned-blueprint rows: holds the display
   * name and the set of distinct owner {@code sub}s seen for one product key.
   */
  private static final class ProductAggregate {
    private final String productName;
    private final Set<String> owners = new LinkedHashSet<>();

    /**
     * Starts an accumulator for one product.
     *
     * @param productName the product's display spelling
     */
    ProductAggregate(String productName) {
      this.productName = productName;
    }
  }
}
