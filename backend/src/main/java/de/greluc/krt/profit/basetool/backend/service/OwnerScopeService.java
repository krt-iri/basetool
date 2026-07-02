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

package de.greluc.krt.profit.basetool.backend.service;

import de.greluc.krt.profit.basetool.backend.exception.BadRequestException;
import de.greluc.krt.profit.basetool.backend.model.JobOrder;
import de.greluc.krt.profit.basetool.backend.model.MembershipRole;
import de.greluc.krt.profit.basetool.backend.model.Mission;
import de.greluc.krt.profit.basetool.backend.model.OrgUnit;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.profit.basetool.backend.model.RefineryOrder;
import de.greluc.krt.profit.basetool.backend.model.Squadron;
import de.greluc.krt.profit.basetool.backend.model.User;
import de.greluc.krt.profit.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.profit.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.profit.basetool.backend.repository.MissionRepository;
import de.greluc.krt.profit.basetool.backend.repository.OperationRepository;
import de.greluc.krt.profit.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.profit.basetool.backend.repository.OrgUnitRepository;
import de.greluc.krt.profit.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.profit.basetool.backend.repository.ShipRepository;
import de.greluc.krt.profit.basetool.backend.repository.SpecialCommandRepository;
import de.greluc.krt.profit.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.profit.basetool.backend.support.Roles;
import de.greluc.krt.profit.basetool.backend.support.StaffelMembershipResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the org-unit context of the current request and answers the "may the caller see / edit
 * this org-unit-scoped data?" questions that gate every {@code @PreAuthorize} on the org-unit-
 * scoped aggregates (mission, hangar, inventory, refinery, operation).
 *
 * <p>This bean is the R2.c generalisation of the original {@code SquadronScopeService}. The
 * implementation logic is identical — single-table inheritance means an org unit with {@code kind =
 * 'SQUADRON'} loads as a {@link Squadron} instance and is reachable through the same {@link
 * SquadronRepository#findById(Object)} call path. What changes is the *naming surface*: the service
 * exposes both the historical {@code …Squadron(…)} method names (so the existing
 * {@code @PreAuthorize("@squadronScopeService.canSeeSquadron(#id)")} SpEL strings keep resolving
 * via the compatibility shim) and the plan-aligned {@code …OrgUnit(…)} aliases that R2.d will
 * migrate the SpEL strings onto.
 *
 * <p>Two org-unit contexts feed into the resolution:
 *
 * <ul>
 *   <li>For a non-admin user, the persistent {@code app_user.squadron_id} they were assigned to.
 *       (Once R2.d switches the membership-driven scope resolution on, this falls back to the
 *       user's Staffel membership in {@code org_unit_membership} — for now the legacy column is
 *       still authoritative.)
 *   <li>For an admin, the {@link #ACTIVE_ORG_UNIT_HEADER} request header relayed by the frontend's
 *       WebClient. {@code null} / missing means "all squadrons" — admins are not constrained when
 *       no active selection exists.
 * </ul>
 *
 * <p>The class-level {@code @Transactional(readOnly = true)} mirrors the {@code
 * SquadronScopeService} setting it inherited — every repository call here is read-only, and lets
 * Spring skip the dirty-check flush and route to the read replica if one is configured.
 *
 * <p>Bean identity: this is the {@code ownerScopeService} bean (auto-named from the class). The
 * {@code SquadronScopeService} shim is the {@code squadronScopeService} bean and delegates every
 * method to this class — see that class for the rationale.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnerScopeService {

  /**
   * Name of the HTTP request header through which the frontend relays the caller's active OrgUnit
   * selection. A {@code null}/missing value means "no active selection" (admin sees all OrgUnits);
   * a non-blank UUID restricts the request to that OrgUnit's data for its duration. Source of truth
   * lives on the frontend (Redis-backed Spring Session via {@code MeFrontendController}); the
   * backend treats the header as untrusted-but-bounded input — an admin pin is honoured directly, a
   * non-admin pin only when it matches one of the caller's memberships (re-validated in {@link
   * #currentScopePredicate()}). Read by {@link #readActiveSquadronFromHeader()}.
   */
  public static final String ACTIVE_ORG_UNIT_HEADER = "X-Active-Org-Unit-Id";

  /**
   * Request-attribute key under which the result of {@link #readPersistentSquadronFromUser()} is
   * cached for the duration of the current HTTP request. Stored as {@code Optional<UUID>} (never
   * {@code null}) so the cache can distinguish "resolved to empty" from "not yet resolved".
   */
  private static final String CACHE_KEY_PERSISTENT_USER_SQUADRON_ID =
      OwnerScopeService.class.getName() + ".persistentUserSquadronId";

  /**
   * Request-attribute key under which the result of {@link #currentSquadron()} is cached for the
   * duration of the current HTTP request. Same distinction-via-Optional contract as {@link
   * #CACHE_KEY_PERSISTENT_USER_SQUADRON_ID}.
   */
  private static final String CACHE_KEY_CURRENT_SQUADRON =
      OwnerScopeService.class.getName() + ".currentSquadron";

  /**
   * Request-attribute key under which {@link #currentMemberOrgUnitIds()} caches the caller's
   * membership org-unit ids for the duration of the current HTTP request. Stored as the resolved
   * {@code Set<UUID>} (possibly empty) directly — a present attribute of type {@link java.util.Set}
   * means "already resolved this request", so the membership read happens at most once even though
   * several gates consult it.
   */
  private static final String CACHE_KEY_MEMBER_ORG_UNIT_IDS =
      OwnerScopeService.class.getName() + ".memberOrgUnitIds";

  /**
   * Request-attribute key under which {@link #currentCallerMemberships()} caches the current
   * caller's raw {@code org_unit_membership} rows for the duration of the request. The blueprint-
   * overview gate plus the cascading and own-level oversight scopes each read the same membership
   * list; memoising it collapses those repeated {@code findAllByIdUserId} reads (e.g. the gate +
   * body double-read on the availability overview) to a single query per request.
   */
  private static final String CACHE_KEY_CALLER_MEMBERSHIPS =
      OwnerScopeService.class.getName() + ".callerMemberships";

  /**
   * Request-attribute key under which {@link #canViewJobOrders()} caches its boolean verdict for
   * the duration of the current HTTP request. The profit-eligibility gate is request-constant (it
   * derives only from the caller's memberships), yet on the order <em>lookup</em> path it is
   * consulted once per row via the {@code canSeeJobOrder} filter; memoising the verdict collapses
   * the otherwise-repeated {@code countProfitEligibleByIdIn} aggregate to a single query per
   * request. A present attribute of type {@link Boolean} means "already resolved this request".
   */
  private static final String CACHE_KEY_CAN_VIEW_JOB_ORDERS =
      OwnerScopeService.class.getName() + ".canViewJobOrders";

  private final AuthHelperService authHelper;
  private final SquadronRepository squadronRepository;
  private final SpecialCommandRepository specialCommandRepository;
  private final MissionRepository missionRepository;
  private final JobOrderRepository jobOrderRepository;
  private final InventoryItemRepository inventoryItemRepository;
  private final RefineryOrderRepository refineryOrderRepository;
  private final OperationRepository operationRepository;
  private final ShipRepository shipRepository;
  private final OrgUnitMembershipRepository orgUnitMembershipRepository;
  private final OrgUnitRepository orgUnitRepository;
  private final OrgUnitCascadeService orgUnitCascadeService;
  private final StaffelMembershipResolver staffelMembershipResolver;
  private final HttpServletRequest request;

  /**
   * Returns the org-unit context that filters the current request. For admins this reads the {@code
   * X-Active-Org-Unit-Id} request header (the frontend's switcher pushed there via the relay
   * filter); for everyone else this loads the user's persistent home squadron. Empty result means
   * "no filter" for admins ("all squadrons") and "no access" for non-admins (typically
   * unauthenticated / anonymous).
   *
   * <p>The non-admin branch's {@code org_unit_membership} lookup is memoised on the {@link
   * HttpServletRequest} via {@link #readPersistentSquadronFromUser()}, so repeated calls within the
   * same request collapse to a single DB hit. The admin branch reads the request header (in-memory)
   * and is not cached separately — it is already constant-time.
   *
   * @return the active org-unit id, or empty when no filter applies.
   */
  @NotNull
  public Optional<UUID> currentSquadronId() {
    if (authHelper.isAdmin()) {
      return readActiveSquadronFromHeader();
    }
    return readPersistentSquadronFromUser();
  }

  /**
   * Plan-aligned alias for {@link #currentSquadronId()}. R2.d will start migrating call sites to
   * the org-unit-named API; the existing {@code currentSquadronId()} method stays so the
   * compatibility shim keeps working without translation.
   *
   * @return the active org-unit id, or empty when no filter applies.
   */
  @NotNull
  public Optional<UUID> currentOrgUnitId() {
    return currentSquadronId();
  }

  /**
   * R6.c / SPEZIALKOMMANDO_PLAN.md §3.5 + §5.4: returns the full effective scope vector for the
   * current request, encoded as a {@link ScopePredicate}. Repository queries combine the three
   * fields ({@code adminAllScope}, {@code activeOrgUnitId}, {@code memberOrgUnitIds}) into a single
   * JPQL clause that handles every caller class — admin all-scope, admin/non-admin pinned to a
   * specific OrgUnit, non-admin with multi-membership union, and anonymous — with the same
   * predicate shape. Before R6.c the staffel-scoped queries took a single nullable {@code
   * scopeSquadronId} that collapsed admin-all and non-admin-with-multi-membership into the same
   * code path, silently hiding SK data from multi-membership users.
   *
   * <p>Resolution flow:
   *
   * <ul>
   *   <li>Admin without active header → {@code adminAllScope=true}, all other fields empty.
   *   <li>Admin with active header → {@code activeOrgUnitId=header value}, {@code adminAllScope=
   *       false}, memberships empty (admins do not constrain by their own memberships even when
   *       they happen to have some).
   *   <li>Non-admin (with or without future R5.e pinning) → {@code memberOrgUnitIds = union of
   *       User.squadron + SK memberships}, {@code adminAllScope=false}, {@code activeOrgUnitId=
   *       null}. The R5.e pinning will switch this branch to populate {@code activeOrgUnitId} from
   *       the same X-Active-Org-Unit-Id header that admins use today.
   *   <li>Anonymous → all empty / false / null. The repository predicate falls through to "no rows
   *       except cross-staffel public escape".
   * </ul>
   *
   * <p>The membership union read is hybrid pre-D3: {@link User#getSquadron()} for the Staffel link
   * (still authoritative on {@code app_user.squadron_id}) plus {@link
   * OrgUnitMembershipRepository#findAllByIdUserIdAndKind} for SK rows. Once D3 drops {@code
   * app_user.squadron_id} and migrates the legacy Staffel membership onto {@code
   * org_unit_membership}, this method switches to a single {@code findAllByIdUserId} read.
   *
   * @return a never-null scope vector describing what the current request should see.
   */
  @NotNull
  public ScopePredicate currentScopePredicate() {
    if (authHelper.isAdmin()) {
      Optional<UUID> active = readActiveSquadronFromHeader();
      return active
          .map(id -> new ScopePredicate(false, id, java.util.Set.of()))
          .orElseGet(() -> new ScopePredicate(true, null, java.util.Set.of()));
    }
    // R5.e: non-admin path. Read the same active-OrgUnit header the admin switcher uses — once
    // the frontend's R5.e switcher widening lets non-admins pick from their memberships, the
    // header carries that selection. The pin is only honoured when it points to a unit the caller
    // can actually reach — their direct memberships unioned with the epic #692 / REQ-ORG-015
    // cascade, so a Bereichsleitung/OL may pin to a descendant unit but never to a foreign one.
    // This is the defence against a spoofed header from a curl call; a pin outside that reach
    // silently collapses to the reach-union read so the user never sees data they did not opt into.
    java.util.Set<UUID> memberOrgUnitIds = currentMemberOrgUnitIds();
    Optional<UUID> pinned = readActiveSquadronFromHeader();
    if (pinned.isPresent() && memberOrgUnitIds.contains(pinned.get())) {
      return new ScopePredicate(false, pinned.get(), java.util.Set.of());
    }
    return new ScopePredicate(false, null, memberOrgUnitIds);
  }

  /**
   * Effective scope for the hangar <b>unit overview</b> ({@code /hangar/squadron}, REQ-HANGAR-003).
   * Identical to {@link #currentScopePredicate()} for every caller except one owner-approved
   * widening: a non-pinned <b>OL member</b> sees <em>every</em> ship — including the ownerless
   * personal ships ({@code owningOrgUnit == null}) of members who belong to no org unit at all — so
   * the OL's cross-org view of the fleet is complete.
   *
   * <p>This is a deliberate, narrowly-scoped exception to the REQ-ORG-015 hard invariant
   * ("OL/Bereich leadership never inherits the admin carve-outs, including ownerless-row access"):
   * it grants the {@code adminAllScope=true} read <em>only</em> for this one aggregation surface
   * and confers no {@code isAdmin()} rights anywhere else — every other {@code can*} gate and
   * scoped list still routes OL through the concrete-membership-union {@link
   * #currentScopePredicate()}. The exception is recorded in ADR-0048 and amends REQ-ORG-015 in
   * {@code org-unit-tenancy.md}.
   *
   * <p>The widening applies only when <b>no single unit is pinned</b> (owner decision: a pin still
   * narrows the unit overview to the pinned unit, like every other scoped surface) and only to an
   * OL member — a plain or BL member keeps their exact membership/cascade reach (their own
   * Staffeln/SKs, and a BL their Bereich's Staffeln/SKs), and an admin keeps the unchanged
   * admin-all / admin-pin behaviour.
   *
   * @return the unit-overview scope vector: {@code adminAllScope} for a non-pinned OL member,
   *     otherwise exactly {@link #currentScopePredicate()}.
   */
  @NotNull
  public ScopePredicate currentUnitOverviewScope() {
    ScopePredicate base = currentScopePredicate();
    // Only upgrade a non-admin OL member who has not pinned a single unit; everyone else (admins,
    // plain/BL members, and any pinned caller) keeps the base scope unchanged.
    if (!base.adminAllScope()
        && base.activeOrgUnitId() == null
        && !authHelper.isAdmin()
        && currentUserIsOlMember()) {
      return new ScopePredicate(true, null, java.util.Set.of());
    }
    return base;
  }

  /**
   * Resolves the SQUADRON-scope <em>set</em> for the admin user-list / search / typeahead /
   * promotion-Bewertungsmatrix queries. REQ-ORG-017 relaxed a member to up to two Staffeln, so the
   * unpinned non-admin scope is the <b>union</b> of the caller's own Staffeln rather than a single
   * one. A faithful generalisation of {@link #currentSquadronId()} (the pre-REQ-ORG-017 single
   * value) that only widens the two-Staffel case and otherwise preserves the legacy behaviour
   * exactly:
   *
   * <ul>
   *   <li>admin without a pin → {@code null} (no filter — the cross-staffel list);
   *   <li>admin/non-admin with an active pin → the singleton pinned id;
   *   <li>non-admin without a pin and at least one Staffel → the set of the caller's own Staffel
   *       ids (one element for a single-Staffel member — byte-identical to before; two for a
   *       dual-Staffel member);
   *   <li>non-admin without a pin and no Staffel → {@code null}, preserving the legacy "unfiltered"
   *       behaviour (so Bereich/OL leadership and guests keep seeing the full picker list).
   * </ul>
   *
   * <p>The returned set is never empty (a caller with no Staffel collapses to {@code null}), so the
   * repository {@code IN :scopeSquadronIds} clause never degenerates to an {@code IN ()}.
   *
   * @return the squadron id set the user-list queries filter on, or {@code null} for the unfiltered
   *     admin/leadership all-scope.
   */
  @org.jetbrains.annotations.Nullable
  public Set<UUID> currentUserListScopeSquadronIds() {
    if (authHelper.isAdmin()) {
      Optional<UUID> pin = readActiveSquadronFromHeader();
      return pin.<Set<UUID>>map(Set::of).orElse(null);
    }
    Optional<UUID> callerId = authHelper.currentUserId();
    if (callerId.isEmpty()) {
      return null;
    }
    List<OrgUnitMembership> rows =
        orgUnitMembershipRepository.findAllByIdUserIdAndKind(callerId.get(), OrgUnitKind.SQUADRON);
    if (rows.isEmpty()) {
      return null;
    }
    Optional<UUID> pinned = readActiveSquadronFromHeader();
    if (pinned.isPresent()
        && rows.stream().anyMatch(r -> r.getId().getOrgUnitId().equals(pinned.get()))) {
      return Set.of(pinned.get());
    }
    Set<UUID> ids = new LinkedHashSet<>();
    for (OrgUnitMembership m : rows) {
      ids.add(m.getId().getOrgUnitId());
    }
    return ids;
  }

  /**
   * {@code true} iff the current non-admin caller holds more than one Staffel (REQ-ORG-017) and has
   * NOT pinned one of them via the active-context switcher — i.e. a single-Staffel auto-stamp would
   * have to pick arbitrarily. Create flows that auto-stamp exactly one owning Staffel (e.g. the
   * promotion topic / rank-requirement create) consult this to honour the owner's "pin, else
   * choose" decision: when it returns {@code true} they reject the create with a clean 400 telling
   * the user to pin the target Staffel first, rather than silently defaulting to the name-sorted
   * primary. Admins are never ambiguous here (they always stamp via an explicit switcher focus or
   * are rejected in "all squadrons" mode), and a caller with zero or one Staffel is unambiguous.
   *
   * @return {@code true} iff a single-Staffel auto-stamp would be ambiguous for the current caller.
   */
  public boolean hasAmbiguousStaffelContext() {
    if (authHelper.isAdmin()) {
      return false;
    }
    Optional<UUID> callerId = authHelper.currentUserId();
    if (callerId.isEmpty()) {
      return false;
    }
    List<OrgUnitMembership> rows =
        orgUnitMembershipRepository.findAllByIdUserIdAndKind(callerId.get(), OrgUnitKind.SQUADRON);
    if (rows.size() <= 1) {
      return false;
    }
    Optional<UUID> pinned = readActiveSquadronFromHeader();
    return pinned.isEmpty()
        || rows.stream().noneMatch(r -> r.getId().getOrgUnitId().equals(pinned.get()));
  }

  /**
   * Helper for {@link #currentScopePredicate()}: resolves every OrgUnit id the current non-admin
   * caller has effective reach over — their direct memberships <em>plus</em> the cascading
   * leadership expansion (epic #692, REQ-ORG-015). Used by the union-of-memberships branch of the
   * scope predicate. Returns the empty set for anonymous callers and (technically) for admins,
   * although the latter never reaches this method through {@link #currentScopePredicate()}.
   *
   * <p>Cascade (delegated to {@link
   * OrgUnitCascadeService#expandWithDescendants(java.util.Collection)} so the converter and this
   * resolver share one definition): a Bereichsleitung membership ({@code is_bereichsleiter}/{@code
   * is_bereichskoordinator}/{@code is_bereichsoperator}) widens reach to the Bereich's Staffeln +
   * SKs; an OL membership ({@code is_ol_member}) widens reach to <em>every</em> org unit; a plain
   * Staffel/SK membership (or a flag-less Bereich/OL seat) widens nothing. The expansion is always
   * a concrete id set — never {@code adminAllScope}, never an {@code isAdmin()} grant — so
   * OL/Bereich leadership stays officer-equivalent and never inherits the admin carve-outs (the
   * HARD INVARIANT of REQ-ORG-015). For a caller with no leadership flag the result is exactly
   * their direct membership ids, i.e. byte-for-byte the pre-#692 behaviour.
   *
   * <p>The result is memoised on the {@link HttpServletRequest} for the duration of the request.
   * Since the Job-Order profit gate ({@link #canViewJobOrders()}) landed, the resolver is consulted
   * more than once per request — the list path reads it via both {@code canViewJobOrders()} and
   * {@link #currentScopePredicate()}, and the detail/edit paths read it via {@code
   * canViewJobOrders()} — so the single cached {@code Set<UUID>} collapses what would otherwise be
   * repeated {@code findAllByIdUserId} + hierarchy reads into one.
   *
   * <p>Post-D3: every membership row (Staffel + SK + Bereich + OL) flows through {@code
   * OrgUnitMembershipRepository.findAllByIdUserId} — the legacy {@code User.squadron} column was
   * dropped in R9 Step 5 / V101.
   *
   * @return the union of OrgUnit ids the caller is a member of or has cascading leadership reach
   *     over, never {@code null}.
   */
  @NotNull
  public java.util.Set<UUID> currentMemberOrgUnitIds() {
    Object cached = request.getAttribute(CACHE_KEY_MEMBER_ORG_UNIT_IDS);
    if (cached instanceof java.util.Set<?> set) {
      @SuppressWarnings("unchecked")
      java.util.Set<UUID> typed = (java.util.Set<UUID>) set;
      return typed;
    }
    // Reuse the request-memoised membership rows (REQ-DATA-003): the blueprint-overview gate
    // and the oversight scopes now share one membership read. currentCallerMemberships() is
    // empty for anonymous callers and expandWithDescendants of an empty input is the empty
    // set, so both the anonymous and member paths behave exactly as before.
    java.util.Set<UUID> ids =
        orgUnitCascadeService.expandWithDescendants(currentCallerMemberships());
    request.setAttribute(CACHE_KEY_MEMBER_ORG_UNIT_IDS, ids);
    return ids;
  }

  /**
   * The current caller's raw {@code org_unit_membership} rows, memoised on the request. Returns an
   * empty list for an anonymous caller. Shared by the blueprint-overview gate and the cascading /
   * own-level oversight scopes so they read the membership table once per request instead of once
   * each (REQ-DATA-003).
   *
   * @return the caller's membership rows, never {@code null}.
   */
  @NotNull
  private List<OrgUnitMembership> currentCallerMemberships() {
    Object cached = request.getAttribute(CACHE_KEY_CALLER_MEMBERSHIPS);
    if (cached instanceof List<?> list) {
      @SuppressWarnings("unchecked")
      List<OrgUnitMembership> typed = (List<OrgUnitMembership>) list;
      return typed;
    }
    List<OrgUnitMembership> memberships =
        authHelper
            .currentUserId()
            .map(orgUnitMembershipRepository::findAllByIdUserId)
            .orElseGet(List::of);
    request.setAttribute(CACHE_KEY_CALLER_MEMBERSHIPS, memberships);
    return memberships;
  }

  /**
   * {@code true} iff the current principal may enter the Job-Order area at all — i.e. see the order
   * list and order details. Only members of a <em>profit-eligible</em> org unit (Squadron or
   * Spezialkommando) participate in the order workflow: Kartell departments are split into Profit
   * and non-Profit, and only the Profit side processes orders. A non-Profit unit may still
   * <em>place</em> orders (as the requesting/Auftraggeber side) but its members must not see the
   * order queue — mirroring how anonymous guests can submit an order yet cannot track it.
   *
   * <ul>
   *   <li>Admin → always {@code true} (system-wide oversight, like every other {@code can*}
   *       short-circuit here).
   *   <li>Non-admin → {@code true} iff at least one of the caller's membership org units (any kind)
   *       is flagged {@code is_profit_eligible}.
   *   <li>Anonymous / member of only non-profit units → {@code false}.
   * </ul>
   *
   * <p>This is the viewer-side gate folded into {@link #canSeeJobOrder(UUID)} (so order details +
   * material claims respect it) and short-circuited by {@code JobOrderService.getAllJobOrders} (so
   * the list returns empty). It is independent of which specific order is responsible to whom — a
   * non-profit member sees nothing, including the otherwise-public SK queue.
   *
   * @return {@code true} iff the caller may view job orders.
   */
  public boolean canViewJobOrders() {
    if (request.getAttribute(CACHE_KEY_CAN_VIEW_JOB_ORDERS) instanceof Boolean cached) {
      return cached;
    }
    boolean verdict = resolveCanViewJobOrders();
    request.setAttribute(CACHE_KEY_CAN_VIEW_JOB_ORDERS, verdict);
    return verdict;
  }

  /**
   * Computes the {@link #canViewJobOrders()} verdict without the request-scoped memo — the admin
   * short-circuit, the empty-membership rejection, and the profit-eligibility count. Split out so
   * the public method only owns the caching.
   *
   * @return {@code true} iff the caller is an admin or a member of at least one profit-eligible org
   *     unit.
   */
  private boolean resolveCanViewJobOrders() {
    if (authHelper.isAdmin()) {
      return true;
    }
    Set<UUID> memberOrgUnitIds = currentMemberOrgUnitIds();
    if (memberOrgUnitIds.isEmpty()) {
      return false;
    }
    return orgUnitRepository.countProfitEligibleByIdIn(memberOrgUnitIds) > 0;
  }

  /**
   * #364 blueprint-availability gate: {@code true} iff the current principal may open the org-unit
   * blueprint availability overview at all. The overview is an oversight feature restricted to
   * leadership:
   *
   * <ul>
   *   <li>admins — always (they see every org unit, or the one they pinned);
   *   <li>officers — for their own Staffel;
   *   <li>Spezialkommando leads — for the SK(s) they lead;
   *   <li>Bereichsleitung / OL members — for their Bereich's (or every) unit (epic #692 Phase 6).
   * </ul>
   *
   * <p>A plain member, or a contextual logistician who holds no oversight seat, is rejected (no
   * menu entry, empty / forbidden API). The leadership branch scans the caller's memberships for
   * any oversight seat — see {@link #isOversightSeat(OrgUnitMembership)}: an SK-lead ({@code
   * is_lead}, pinned to SK memberships by the V95 CHECK) or — since epic #692 Phase 6 — a
   * Bereichsleitung / OL membership.
   *
   * @return {@code true} iff the caller is an admin, an officer, or holds at least one oversight
   *     seat (SK-lead / Bereichsleitung / OL).
   */
  public boolean canAccessBlueprintOverview() {
    if (authHelper.isAdmin() || authHelper.hasReachableRole(Roles.authority(Roles.OFFICER))) {
      return true;
    }
    return currentCallerMemberships().stream().anyMatch(OwnerScopeService::isOversightSeat);
  }

  /**
   * #364 effective scope for the blueprint-availability overview <em>and</em> the org-unit bank
   * balance-view (F1, REQ-BANK-021), encoded as a {@link ScopePredicate} so the aggregation reuses
   * the same three-field shape as the staffel-scoped list queries. Unlike {@link
   * #currentScopePredicate()} — which returns the union of <em>all</em> of a non-admin's
   * memberships — this restricts a non-admin to the org units they have oversight over, mirroring
   * {@link #canAccessBlueprintOverview()}. This is the <b>cascading</b> (view) scope — it drills
   * down into subordinate units:
   *
   * <ul>
   *   <li>admin → delegates to {@link #currentScopePredicate()} (all org units, or the pinned one);
   *   <li>officer → their own Staffel (via {@link #readPersistentSquadronFromUser()});
   *   <li>SK lead → every SK they lead;
   *   <li>Bereichsleitung → their Bereich <em>and</em> every Staffel/SK of it; OL member → every
   *       org unit — the cascading, officer-equivalent reach (epic #692 Phase 6, REQ-ORG-015) via
   *       {@link OrgUnitCascadeService#cascadedOfficerReach(java.util.Collection)}, which also
   *       contributes the Bereich/OL seat itself so the caller's own AREA/CARTEL account is in
   *       scope. Never an admin-all marker (the HARD INVARIANT);
   *   <li>an active pin is honoured only when it points at one of those oversight org units,
   *       otherwise it is ignored and the full oversight union applies.
   * </ul>
   *
   * <p>A caller with an empty oversight set (e.g. a plain member who reached the service despite
   * the gate) yields {@code memberOrgUnitIds = {}}, which the aggregation treats as "no rows".
   *
   * <p>This cascading scope is for <b>reads</b> (view). The own-level write scope a
   * Bereichsleitung/OL may raise a bank booking request against (F2, REQ-BANK-022, owner decision
   * Q4) is {@link #currentOwnLevelOversightScope()} — deliberately <em>not</em> cascaded, so a
   * subordinate account reached by drill-down is view-only.
   *
   * @return a never-null cascading scope vector of the org units whose data the caller may oversee.
   */
  @NotNull
  public ScopePredicate currentOversightScope() {
    if (authHelper.isAdmin()) {
      return currentScopePredicate();
    }
    Set<UUID> oversightOrgUnitIds = new LinkedHashSet<>();
    List<OrgUnitMembership> memberships = currentCallerMemberships();
    if (authHelper.hasReachableRole(Roles.authority(Roles.OFFICER))) {
      // REQ-ORG-017: an officer oversees ALL of their own Staffeln (up to two), not just the
      // name-sorted primary — add every SQUADRON membership rather than the single active one.
      for (OrgUnitMembership m : memberships) {
        if (m.getKind() == OrgUnitKind.SQUADRON) {
          oversightOrgUnitIds.add(m.getId().getOrgUnitId());
        }
      }
    }
    for (OrgUnitMembership m : memberships) {
      // SK leads oversee their own SK; from epic #800 (REQ-ROLE-002) the four squadron ranks
      // oversee their own squadron the same way (officer-equivalent, own-unit only, no cascade).
      if (m.getRole() == MembershipRole.SK_LEAD || m.getRole().isSquadronRank()) {
        oversightOrgUnitIds.add(m.getId().getOrgUnitId());
      }
    }
    // Epic #692 Phase 6 (REQ-ORG-015/-019): a Bereichsleitung member oversees their Bereich + its
    // Staffeln/SKs, an OL member every org unit — the cascading, officer-equivalent reach (never
    // admin). cascadedOfficerReach also contributes the Bereich/OL seat itself, so the caller's own
    // AREA/CARTEL account is in scope.
    oversightOrgUnitIds.addAll(orgUnitCascadeService.cascadedOfficerReach(memberships));
    Optional<UUID> pinned = readActiveSquadronFromHeader();
    if (pinned.isPresent() && oversightOrgUnitIds.contains(pinned.get())) {
      return new ScopePredicate(false, pinned.get(), Set.of());
    }
    return new ScopePredicate(false, null, oversightOrgUnitIds);
  }

  /**
   * Epic #692 Phase 6 (REQ-BANK-022, owner decision Q4): the caller's <b>own-level</b> oversight
   * seats, encoded as a {@link ScopePredicate}. This is the write-side companion of {@link
   * #currentOversightScope()} and is deliberately <em>not</em> cascaded — it names only the org
   * units the caller leads at their own level, never the descendants they may merely view:
   *
   * <ul>
   *   <li>admin → delegates to {@link #currentScopePredicate()} (all org units, or the pinned one);
   *   <li>officer → their own Staffel (the squadron {@code ORG_UNIT} account);
   *   <li>SK lead → every SK they lead (its {@code ORG_UNIT} account);
   *   <li>Bereichsleitung → their Bereich (its {@code AREA} account) — but <em>not</em> the child
   *       Staffel/SK accounts;
   *   <li>OL member → the Organisationsleitung (the {@code CARTEL} account) — but <em>not</em> the
   *       AREA/ORG_UNIT accounts below it.
   * </ul>
   *
   * <p>This backs the org-unit bank booking-request gate ({@code
   * OrgUnitBankAccessService.createBookingRequest}): a Bereichsleitung/OL may raise a
   * confirm-before-post request only against their own-level account, while subordinate accounts
   * reached through the cascading view ({@link #currentOversightScope()}) stay view-only. The
   * officer flow from epic #666 is unchanged — an officer's own-level scope is exactly their
   * Staffel, as before. An active pin is honoured only when it points at one of the caller's
   * own-level seats.
   *
   * @return a never-null, non-cascaded scope vector of the caller's own-level oversight seats.
   */
  @NotNull
  public ScopePredicate currentOwnLevelOversightScope() {
    if (authHelper.isAdmin()) {
      return currentScopePredicate();
    }
    Set<UUID> ownLevelOrgUnitIds = new LinkedHashSet<>();
    List<OrgUnitMembership> memberships = currentCallerMemberships();
    if (authHelper.hasReachableRole(Roles.authority(Roles.OFFICER))) {
      // REQ-ORG-017: an officer's own-level scope is ALL of their Staffeln (up to two), not just
      // the
      // name-sorted primary.
      for (OrgUnitMembership m : memberships) {
        if (m.getKind() == OrgUnitKind.SQUADRON) {
          ownLevelOrgUnitIds.add(m.getId().getOrgUnitId());
        }
      }
    }
    for (OrgUnitMembership m : memberships) {
      if (isOversightSeat(m)) {
        ownLevelOrgUnitIds.add(m.getId().getOrgUnitId());
      }
    }
    Optional<UUID> pinned = readActiveSquadronFromHeader();
    if (pinned.isPresent() && ownLevelOrgUnitIds.contains(pinned.get())) {
      return new ScopePredicate(false, pinned.get(), Set.of());
    }
    return new ScopePredicate(false, null, ownLevelOrgUnitIds);
  }

  /**
   * {@code true} iff the membership is an <em>oversight seat</em> — one that confers
   * officer-equivalent oversight over its own org unit (and, for Bereich/OL, cascading reach below
   * it). These are every membership carrying a functional rank ({@link
   * MembershipRole#confersOwnLevelOversight()}, i.e. {@code role != MEMBER}): the SK-lead seat, the
   * Bereichsleitung / OL seats (epic #692 Phase 6, REQ-ORG-015) and, from epic #800 (REQ-ROLE-002),
   * the four squadron ranks. A rank-less ({@code MEMBER}) Staffel/SK/Bereich/OL seat is not an
   * oversight seat. Shared by {@link #canAccessBlueprintOverview()} and {@link
   * #currentOwnLevelOversightScope()} so the gate and the own-level scope agree on what "oversight"
   * means.
   *
   * @param m the membership row to classify; never {@code null}.
   * @return {@code true} iff the membership grants own-level oversight over its org unit.
   */
  private static boolean isOversightSeat(@NotNull OrgUnitMembership m) {
    return m.getRole().confersOwnLevelOversight();
  }

  /**
   * {@code true} iff the caller holds <b>Bereich- or OL-level oversight</b> — the seniority that,
   * on the org-unit bank page, additionally reveals the cartel-wide special accounts
   * (Sonderkonten), which belong to no single org unit and are therefore not reachable through the
   * org-unit cascade (REQ-BANK-028). Unlike {@link #isOversightSeat(OrgUnitMembership)} this
   * deliberately <em>excludes</em> the SK-lead seat and the officer role: an officer or SK lead
   * oversees only their own unit's account, so they do not get the org-wide special-account view.
   * The seats that qualify are the Bereichsleitung flags ({@code is_bereichsleiter}/{@code
   * is_bereichskoordinator}/{@code is_bereichsoperator}) and the OL flag ({@code is_ol_member}) —
   * now read through {@link MembershipRole#isAreaOrOl()}; a rank-less (chart-only) Bereich/OL
   * membership does not qualify. Admins always qualify — they see every account anyway.
   *
   * <p>This is consulted only by the org-unit-aware bank seam ({@link OrgUnitBankAccessService});
   * it adds no org-unit logic to the bank itself, which stays org-unit-blind (REQ-BANK-008,
   * ADR-0011).
   *
   * @return {@code true} iff the caller is an admin or holds a Bereich-/OL-level oversight seat.
   */
  public boolean currentUserHasAreaOrOlOversight() {
    if (authHelper.isAdmin()) {
      return true;
    }
    return currentCallerMemberships().stream().anyMatch(OwnerScopeService::isAreaOrOlSeat);
  }

  /**
   * {@code true} iff the membership is a Bereich- or OL-level oversight seat — the subset of {@link
   * #isOversightSeat(OrgUnitMembership)} that excludes the SK-lead and squadron-rank seats, read
   * through {@link MembershipRole#isAreaOrOl()} ({@code BEREICHSLEITER} / {@code
   * BEREICHSKOORDINATOR} / {@code BEREICHSOPERATOR} / {@code OL_MEMBER}). Backs {@link
   * #currentUserHasAreaOrOlOversight()}.
   *
   * @param m the membership row to classify; never {@code null}.
   * @return {@code true} iff the membership is a Bereichsleitung or OL seat.
   */
  private static boolean isAreaOrOlSeat(@NotNull OrgUnitMembership m) {
    return m.getRole().isAreaOrOl();
  }

  /**
   * {@code true} iff the caller holds an {@code OL_MEMBER} seat — a member of the
   * Organisationsleitung. Pure membership check (no admin short-circuit), so the org-unit bank seam
   * can use it to resolve the collegial holder of the {@code CARTEL} account and the
   * OL-can-configure-SPECIAL-visibility rule (REQ-BANK-037), where "OL" means the OL body, not the
   * admin carve-out.
   *
   * @return {@code true} iff the caller has at least one {@code OL_MEMBER} membership.
   */
  public boolean currentUserIsOlMember() {
    return currentCallerMemberships().stream()
        .anyMatch(m -> m.getRole() == MembershipRole.OL_MEMBER);
  }

  /**
   * {@code true} iff the caller holds a {@code BEREICHSLEITER} seat on any Bereich. Pure membership
   * check (no admin short-circuit), used by the org-unit bank seam for the SPECIAL-account
   * auto-view rule (every Bereichsleiter sees Sonderkonten, REQ-BANK-037) — deliberately narrower
   * than {@link #currentUserHasAreaOrOlOversight()}, which also includes
   * Bereichskoordinatoren/-operatoren and OL members.
   *
   * @return {@code true} iff the caller has at least one {@code BEREICHSLEITER} membership.
   */
  public boolean currentUserIsBereichsleiter() {
    return currentCallerMemberships().stream()
        .anyMatch(m -> m.getRole() == MembershipRole.BEREICHSLEITER);
  }

  /**
   * {@code true} iff the caller holds exactly the given {@link MembershipRole} on the given org
   * unit. Pure membership check used by the org-unit bank seam to resolve a derived responsible
   * holder (e.g. the {@code STAFFELLEITER} of a Staffel, the {@code BEREICHSLEITER} of a PROFIT
   * Bereich) and to evaluate {@code MEMBERSHIP_ROLE} view grants.
   *
   * @param orgUnitId the org unit to check; never {@code null}
   * @param role the membership role to match; never {@code null}
   * @return {@code true} iff the caller has a membership on that unit carrying that role
   */
  public boolean currentUserHoldsRoleOnOrgUnit(
      @NotNull UUID orgUnitId, @NotNull MembershipRole role) {
    return currentCallerMemberships().stream()
        .anyMatch(m -> m.getId().getOrgUnitId().equals(orgUnitId) && m.getRole() == role);
  }

  /**
   * {@code true} iff the caller is a member of the given org unit at all (any role, including a
   * rank-less {@code MEMBER} seat). Pure membership check used by the org-unit bank seam to
   * evaluate the {@code ALL_MEMBERS} view grant on an org-unit account.
   *
   * @param orgUnitId the org unit to check; never {@code null}
   * @return {@code true} iff the caller has any membership on that unit
   */
  public boolean currentUserIsMemberOfOrgUnit(@NotNull UUID orgUnitId) {
    return currentCallerMemberships().stream()
        .anyMatch(m -> m.getId().getOrgUnitId().equals(orgUnitId));
  }

  /**
   * Convenience entry point for the aggregate-service create paths: returns the {@link Squadron}
   * entity that matches {@link #currentSquadronId()}, loaded from the DB. Empty when the caller has
   * no effective squadron (admin in "all squadrons" mode, guest, or unauthenticated). Services use
   * this to stamp {@code owningSquadron} on newly-created aggregates that have no owner field of
   * their own (e.g. {@code Operation}) — aggregates that DO carry an owner ({@code Ship}, {@code
   * Mission}, …) prefer to derive the squadron from the owner so a future user-squadron move does
   * not silently retag history.
   *
   * <p>Result is memoised per {@link HttpServletRequest} so repeated calls in one request collapse
   * to a single {@code squadronRepository.findById} round-trip.
   *
   * @return the {@link Squadron} for the current effective context, or empty when none applies.
   */
  @NotNull
  public Optional<Squadron> currentSquadron() {
    Optional<Optional<Squadron>> cached = readCachedOptional(CACHE_KEY_CURRENT_SQUADRON);
    if (cached.isPresent()) {
      return cached.get();
    }
    Optional<Squadron> resolved = currentSquadronId().flatMap(squadronRepository::findById);
    request.setAttribute(CACHE_KEY_CURRENT_SQUADRON, resolved);
    return resolved;
  }

  /**
   * Plan-aligned org-unit-typed accessor: returns the {@link OrgUnit} matching {@link
   * #currentOrgUnitId()}. Today the active context is always a {@link Squadron} (the admin switcher
   * accepts only Staffel ids); R2.d will widen the switcher to accept Spezialkommando ids too. The
   * method's return type is already widened so the eventual rollout is a one-line repository swap
   * rather than another signature change.
   *
   * @return the current effective {@link OrgUnit}, or empty when none applies.
   */
  @NotNull
  public Optional<OrgUnit> currentOrgUnit() {
    // Cast through Optional<Squadron> for now — Squadron is the only OrgUnit subtype that can
    // currently be the active context. R2.d will replace this with a polymorphic OrgUnitRepository
    // lookup once Squadron + SpecialCommand are both selectable in the admin switcher.
    return currentSquadron().map(s -> (OrgUnit) s);
  }

  /**
   * Resolves the {@link Squadron} that a newly-created aggregate should be stamped on, honouring an
   * optional R5.d owner-picker output. Centralises the validation that every aggregate-stamping
   * service path (inventory create, refinery-order create, mission create, …) would otherwise have
   * to duplicate.
   *
   * <p>R6.b tightens the contract to the plan §5.5.1 0/1/&gt;1-membership matrix:
   *
   * <ol>
   *   <li><b>0 memberships</b> — {@link BadRequestException}. Admin / guest principals cannot stamp
   *       aggregates; the caller path should not reach this method with a memberless user.
   *   <li><b>1 membership + {@code owningOrgUnitId == null}</b> — auto-stamp that single membership
   *       (preserves today's single-Staffel default for the 100% of users still on the legacy
   *       {@code app_user.squadron_id} link).
   *   <li><b>1 membership + {@code owningOrgUnitId} matches</b> — auto-stamp; the explicit picker
   *       output agrees with the only option, no-op.
   *   <li><b>1 membership + {@code owningOrgUnitId} mismatch</b> — {@link BadRequestException}
   *       (foreign-org-unit forgery).
   *   <li><b>&gt;1 memberships + {@code owningOrgUnitId == null}</b> — {@link BadRequestException}
   *       ("owningOrgUnitId is required"). Before R6.b this path silently stamped the legacy
   *       Staffel, hiding the SK choice from a multi-membership user — see the audit's regression
   *       #4 against the R5.d frontend contract.
   *   <li><b>&gt;1 memberships + {@code owningOrgUnitId} matches one</b> — picker output honoured;
   *       the matched OrgUnit is returned.
   *   <li><b>&gt;1 memberships + {@code owningOrgUnitId} foreign</b> — {@link BadRequestException}
   *       (foreign-org-unit forgery).
   *   <li><b>Picker selects a Spezialkommando</b> — {@link BadRequestException} ("Spezialkommando
   *       ownership not yet supported"). Soft block until the destructive-cleanup release drops the
   *       {@code owning_squadron_id} NOT NULL constraint; until then the picker UI may offer SK
   *       options but the backend reliably rejects them rather than persisting half-stamped rows.
   * </ol>
   *
   * <p>Membership sources (hybrid until the §5.2 D3 migration replaces {@code User.squadron} with
   * an authoritative membership-table read):
   *
   * <ul>
   *   <li>{@link User#getSquadron()} — the user's home Staffel; non-null today for every user. Read
   *       first because the legacy column is still authoritative for the Staffel link.
   *   <li>{@link OrgUnitMembershipRepository#findAllByIdUserIdAndKind} with kind {@link
   *       OrgUnitKind#SPECIAL_COMMAND} — the SK memberships added via the R5.b endpoints. SK
   *       memberships are never reflected on {@link User} so the repository read is the only
   *       source. The kind=SQUADRON rows backfilled by V95 are not consulted here — the
   *       authoritative Staffel comes from {@link User#getSquadron()}.
   * </ul>
   *
   * <p>Once D3 lands and {@code User.squadron} is removed, this method switches to a single {@link
   * OrgUnitMembershipRepository#findAllByIdUserId} read.
   *
   * @param targetUser the user the new aggregate belongs to (e.g. the inventory item's owner, the
   *     refinery order's owner); never {@code null}.
   * @param owningOrgUnitId the picker output from the form, or {@code null} when the picker was not
   *     used.
   * @return the Squadron whose stock / aggregate list this row should join; never {@code null}.
   * @throws BadRequestException when the picker output references an org unit the target user does
   *     not belong to, the user has zero memberships, the user has multiple memberships and no
   *     explicit choice was supplied, or the resolved org unit is a Spezialkommando.
   */
  public Squadron resolveSquadronForPickerOutput(@NotNull User targetUser, UUID owningOrgUnitId) {
    Set<UUID> memberOrgUnitIds = new LinkedHashSet<>();
    // Post-D3: every membership (Staffel + SK) is sourced from org_unit_membership — the legacy
    // User.squadron column was dropped in R9 Step 5 / V101.
    List<OrgUnitMembership> allMemberships =
        orgUnitMembershipRepository.findAllByIdUserId(targetUser.getId());
    for (OrgUnitMembership m : allMemberships) {
      memberOrgUnitIds.add(m.getId().getOrgUnitId());
    }

    if (memberOrgUnitIds.isEmpty()) {
      throw new BadRequestException(
          "User has no org-unit membership — cannot stamp an aggregate owner");
    }

    UUID stampedOrgUnitId;
    if (owningOrgUnitId == null) {
      if (memberOrgUnitIds.size() == 1) {
        stampedOrgUnitId = memberOrgUnitIds.iterator().next();
      } else {
        // REQ-ORG-017 "pin, else choose": honour an active-context pin onto one of the target's own
        // org units (self-service create) so a pinned member need not re-pick; otherwise force a
        // choice.
        Optional<UUID> pinned = readActiveSquadronFromHeader();
        if (pinned.isPresent() && memberOrgUnitIds.contains(pinned.get())) {
          stampedOrgUnitId = pinned.get();
        } else {
          throw new BadRequestException(
              "User belongs to multiple org units; owningOrgUnitId is required");
        }
      }
    } else {
      if (!memberOrgUnitIds.contains(owningOrgUnitId)) {
        throw new BadRequestException(
            "Selected owner org unit is not a membership of the target user");
      }
      stampedOrgUnitId = owningOrgUnitId;
    }

    return squadronRepository
        .findById(stampedOrgUnitId)
        .orElseThrow(
            () ->
                new BadRequestException(
                    "Spezialkommando ownership of this aggregate is not yet supported"));
  }

  /**
   * V99-aligned successor of {@link #resolveSquadronForPickerOutput(User, UUID)} — applies the same
   * SPEZIALKOMMANDO_PLAN.md §5.5.1 picker-output matrix (0 / 1 / &gt;1 memberships, valid / foreign
   * choice) but returns an {@link OrgUnit} so SK selections are honoured instead of rejected. Use
   * with {@code entity.setOwningOrgUnit(...)}; the existing entity dual-write lifecycle hook
   * mirrors the value onto the legacy {@code owningSquadron} field whenever the resolved OrgUnit
   * happens to be a {@link Squadron}, so the legacy column stays populated for Staffel ownership
   * during the V99-NOT-NULL-relaxed soak. For SpecialCommand ownership the legacy column stays null
   * — which is now valid because V99 dropped the {@code NOT NULL} constraint.
   *
   * <p>If the resolver produces an SK selection (allowed post-V99 with the lifted NOT NULL on the
   * legacy column), the caller writes only the new {@code owningOrgUnitId} via {@code
   * entity.setOwningOrgUnit(...)}. The lifecycle hook leaves the legacy column null for that row,
   * which is now legal.
   *
   * <p>Decision matrix (extends the legacy {@link #resolveSquadronForPickerOutput(User, UUID)}
   * matrix, which stays strict): 0 memberships → 400; 1 + null picker → auto-stamp the sole direct
   * membership; &gt;1 + null picker → 400 (force an explicit choice). An explicit pick is honoured
   * when it is one of the target user's DIRECT memberships <em>or</em> — epic #692 Phase 4 /
   * REQ-ORG-016 — an org unit the current <b>caller</b> may edit ({@link #canEditOrgUnit(UUID)},
   * cascade-aware), the create-on-behalf widening; a pick that is neither → 400. Because of that
   * widening this resolver and the still-strict (membership-only) {@code
   * resolveSquadronForPickerOutput} no longer agree byte-for-byte: a pick foreign to the target
   * user but within the caller's editable scope is rejected by the latter and honoured here.
   *
   * @param targetUser the user whose memberships gate the picker output validation; never {@code
   *     null}.
   * @param owningOrgUnitId the picker-supplied org unit id; {@code null} triggers the auto-stamp
   *     path when the user has exactly one membership.
   * @return the resolved {@link OrgUnit} — a {@link Squadron}, a {@link
   *     de.greluc.krt.profit.basetool.backend.model.SpecialCommand}, or (Phase 4) a {@link
   *     de.greluc.krt.profit.basetool.backend.model.Bereich} / {@link
   *     de.greluc.krt.profit.basetool.backend.model.Organisationsleitung}; never {@code null}.
   * @throws BadRequestException on 0 memberships, a &gt;1-membership {@code null} picker, or an
   *     explicit pick that is neither a direct membership of the target user nor within the
   *     caller's editable scope.
   */
  public OrgUnit resolveOrgUnitForPickerOutput(@NotNull User targetUser, UUID owningOrgUnitId) {
    Set<UUID> memberOrgUnitIds = collectMemberOrgUnitIds(targetUser);
    if (memberOrgUnitIds.isEmpty()) {
      throw new BadRequestException(
          "User has no org-unit membership — cannot stamp an aggregate owner");
    }
    return resolveStampedOrgUnit(memberOrgUnitIds, owningOrgUnitId);
  }

  /**
   * Nullable-owner variant of {@link #resolveOrgUnitForPickerOutput(User, UUID)} for the three
   * <em>ownerless-personal-aggregate</em> roots (ship, refinery order, inventory item). Behaves
   * identically to the strict resolver, with one carve-out: a {@code targetUser} who belongs to no
   * org unit <em>and</em> supplied no explicit picker output resolves to {@code null} instead of a
   * 400. That {@code null} is a legal owner for these three aggregates — V132 dropped the {@code
   * NOT NULL} on their {@code owning_org_unit_id} column precisely so a membershipless user can
   * still add a ship, raise a refinery order, or record inventory. The row is then attributable
   * through its own per-user owner column ({@code ship.owner} / {@code refinery_order.owner} /
   * {@code inventory_item.user}) and is scoped to that user only — see {@link #canSeeShip(UUID)},
   * {@link #canSeeRefineryOrder(UUID)}, {@link #canSeeInventoryItem(UUID)}.
   *
   * <p>The carve-out is deliberately narrow: a membershipless user who nonetheless supplies a
   * non-null {@code owningOrgUnitId} is still rejected — they cannot claim ownership of an org unit
   * they do not belong to. Every other branch of the SPEZIALKOMMANDO_PLAN.md §5.5.1 matrix (1 /
   * &gt;1 memberships; valid / foreign / multi-membership-null choice) is unchanged from the strict
   * resolver.
   *
   * @param targetUser the user whose memberships gate the picker output; never {@code null}.
   * @param owningOrgUnitId the picker-supplied org unit id, or {@code null} when the picker was not
   *     used.
   * @return the resolved {@link OrgUnit}, or {@code null} when {@code targetUser} has no membership
   *     and supplied no explicit choice (the ownerless-personal-aggregate case).
   * @throws BadRequestException for every non-ownerless rejection branch of the §5.5.1 matrix,
   *     including a membershipless user who supplied a non-null (therefore foreign) choice.
   */
  @Nullable
  public OrgUnit resolveOrgUnitForPickerOutputNullable(
      @NotNull User targetUser, UUID owningOrgUnitId) {
    Set<UUID> memberOrgUnitIds = collectMemberOrgUnitIds(targetUser);
    if (memberOrgUnitIds.isEmpty()) {
      if (owningOrgUnitId == null) {
        return null;
      }
      throw new BadRequestException(
          "Selected owner org unit is not a membership of the target user");
    }
    return resolveStampedOrgUnit(memberOrgUnitIds, owningOrgUnitId);
  }

  /**
   * Validates and resolves the target org unit for an explicit <b>reassignment</b> of an existing
   * aggregate's owning org unit (REQ-ORG-018 / ADR-0050 — the mission Verwaltung "Zugeordnete
   * Einheit" control). Unlike {@link #resolveOrgUnitForPickerOutputNullable(User, UUID)} this
   * carries <em>no</em> auto-stamp or home-Staffel fallback: the caller picks an explicit target
   * and it is accepted only when it lies within their assignable scope.
   *
   * <p>Permission matrix (the orthogonal second gate on top of the per-aggregate write gate the
   * controller already enforces, e.g. {@code MissionSecurityService.canChangeOwner}):
   *
   * <ul>
   *   <li><b>Admin</b> — any existing org unit, or {@code null} (ownerless), in any direction.
   *   <li><b>Non-admin</b> — a non-null target must be one of the caller's DIRECT memberships OR an
   *       org unit they may edit ({@link #canEditOrgUnit(UUID)}, cascade-aware for a
   *       Bereichsleitung/OL); the same accepted set as the create-on-behalf picker ({@code
   *       resolveStampedOrgUnit}). A {@code null} (ownerless) target is allowed only for a
   *       membershipless leadership caller — mirroring who may <em>create</em> an ownerless mission
   *       (ADR-0004) — so a plain member cannot silently widen a mission to public-leadership
   *       scope.
   * </ul>
   *
   * @param targetOrgUnitId the picker-supplied target org-unit id, or {@code null} for ownerless.
   * @return the resolved managed {@link OrgUnit}, or {@code null} for an ownerless target.
   * @throws org.springframework.security.access.AccessDeniedException when the caller may not
   *     assign to the requested target.
   * @throws BadRequestException when a non-null target id does not resolve to a known org unit.
   */
  @org.jetbrains.annotations.Nullable
  public OrgUnit resolveReassignTargetOrgUnit(
      @org.jetbrains.annotations.Nullable UUID targetOrgUnitId) {
    boolean admin = authHelper.isAdmin();
    if (targetOrgUnitId == null) {
      // Ownerless target: an admin always, otherwise only a membershipless leadership caller. The
      // member lookup is short-circuited for admins.
      if (admin || currentMemberOrgUnitIds().isEmpty()) {
        return null;
      }
      throw new org.springframework.security.access.AccessDeniedException(
          "Only an admin or a membershipless leadership user may make an aggregate ownerless");
    }
    // Non-null target: an admin may assign anywhere; a non-admin only to a direct membership or a
    // unit within their editable (cascade-aware) scope. The `!admin` short-circuit keeps the admin
    // path off the member lookup entirely.
    if (!admin
        && !currentMemberOrgUnitIds().contains(targetOrgUnitId)
        && !canEditOrgUnit(targetOrgUnitId)) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Target org unit is neither a membership of the caller nor within their editable scope");
    }
    return orgUnitRepository
        .findById(targetOrgUnitId)
        .orElseThrow(
            () -> new BadRequestException("owningOrgUnitId does not resolve to a known org unit"));
  }

  /**
   * Collects the distinct org-unit ids {@code targetUser} belongs to from the single authoritative
   * {@code org_unit_membership} source (Staffel and SK rows alike). Insertion order is preserved
   * via {@link LinkedHashSet} so the single-membership auto-stamp in {@link #resolveStampedOrgUnit}
   * is deterministic.
   *
   * @param targetUser the user whose memberships to read; never {@code null}.
   * @return the (possibly empty) set of org-unit ids the user is a member of.
   */
  @NotNull
  private Set<UUID> collectMemberOrgUnitIds(@NotNull User targetUser) {
    Set<UUID> memberOrgUnitIds = new LinkedHashSet<>();
    for (OrgUnitMembership m : orgUnitMembershipRepository.findAllByIdUserId(targetUser.getId())) {
      memberOrgUnitIds.add(m.getId().getOrgUnitId());
    }
    return memberOrgUnitIds;
  }

  /**
   * Applies the §5.5.1 picker-output matrix for a user known to have at least one membership, then
   * resolves the chosen id to its concrete {@link OrgUnit} subtype (Staffel via {@link
   * SquadronRepository}, Spezialkommando via {@link SpecialCommandRepository}, and — epic #692
   * Phase 4 / REQ-ORG-016 — a {@code BEREICH} / {@code ORGANISATIONSLEITUNG} owner via the
   * polymorphic {@link OrgUnitRepository}). Shared tail of {@link
   * #resolveOrgUnitForPickerOutput(User, UUID)} and {@link
   * #resolveOrgUnitForPickerOutputNullable(User, UUID)} — the empty-membership branch differs
   * between the two callers and is handled by each before delegating here.
   *
   * <p>The auto-stamp ({@code owningOrgUnitId == null}) and {@code >1 → force a choice} rules stay
   * keyed on the target user's DIRECT memberships, so a leader's default owner is their own
   * Bereich/OL and ordinary-member stamping is unchanged. An explicit pick is accepted when it is a
   * DIRECT membership <em>or</em> an org unit the current caller may edit ({@link
   * #canEditOrgUnit(UUID)}) — the cascade-aware create-on-behalf widening.
   *
   * @param memberOrgUnitIds the target user's non-empty DIRECT membership set.
   * @param owningOrgUnitId the picker-supplied org unit id, or {@code null} for the auto-stamp
   *     path.
   * @return the resolved {@link OrgUnit}; never {@code null}.
   * @throws BadRequestException on a pick that is neither a direct membership nor within the
   *     caller's editable scope, a &gt;1-membership {@code null} choice, or a resolved id that no
   *     longer exists / is not an ownable kind.
   */
  @NotNull
  private OrgUnit resolveStampedOrgUnit(@NotNull Set<UUID> memberOrgUnitIds, UUID owningOrgUnitId) {
    UUID stampedOrgUnitId;
    if (owningOrgUnitId == null) {
      if (memberOrgUnitIds.size() == 1) {
        stampedOrgUnitId = memberOrgUnitIds.iterator().next();
      } else {
        // REQ-ORG-017 "pin, else choose": honour an active-context pin onto one of the TARGET
        // user's
        // own org units (the self-service create path where caller == target) so a member who has
        // already pinned a Staffel via the switcher need not re-pick it on the create form;
        // otherwise force an explicit choice. The pin is only honoured when it is one of the
        // target's
        // memberships, so an admin's foreign pin on an on-behalf create still falls through to 400.
        Optional<UUID> pinned = readActiveSquadronFromHeader();
        if (pinned.isPresent() && memberOrgUnitIds.contains(pinned.get())) {
          stampedOrgUnitId = pinned.get();
        } else {
          throw new BadRequestException(
              "User belongs to multiple org units; owningOrgUnitId is required");
        }
      }
    } else {
      // Epic #692 Phase 4 (REQ-ORG-016): a picker choice is valid when it is one of the TARGET
      // user's DIRECT memberships (the historical contract) OR an org unit the CURRENT CALLER may
      // edit ({@link #canEditOrgUnit(UUID)}, cascade-aware since Phase 3). The latter is the
      // create-on-behalf widening: a Bereichsleitung/OL leader may stamp a subordinate Staffel/SK
      // (or its own Bereich/OL) they oversee.
      //
      // Note the gate keys canEditOrgUnit on the CALLER, while memberOrgUnitIds is the TARGET
      // user's
      // set. When caller == targetUser (every self-service create path) the two coincide, so an
      // ordinary member's accepted set is exactly their own memberships and stamping is
      // byte-identical
      // to the pre-Phase-4 gate. They DIVERGE only on the two create-on-behalf paths where a caller
      // stamps another user's row — inventory book-out/transfer and refinery store — and there the
      // accepted set is the union of (target's memberships) and (caller's editable scope), by
      // design:
      // a leader may place the recipient's row in any unit the leader already controls. This never
      // widens what the CALLER can see (canEditOrgUnit only admits units already in the caller's
      // scope)
      // and REQ-ORG-011 owner-escape keeps the recipient's own visibility of the row.
      if (!memberOrgUnitIds.contains(owningOrgUnitId) && !canEditOrgUnit(owningOrgUnitId)) {
        throw new BadRequestException(
            "Selected owner org unit is neither a membership of the target user nor within the"
                + " caller's editable scope");
      }
      stampedOrgUnitId = owningOrgUnitId;
    }

    // Resolve to the concrete subtype. Staffel-side: SquadronRepository (discriminator filter
    // matches). SK-side: SpecialCommandRepository. Epic #692 Phase 4 (REQ-ORG-016): a Bereich or
    // Organisationsleitung may own an aggregate directly, so a non-Squadron/non-SK id falls through
    // to the polymorphic OrgUnitRepository and is accepted iff it resolves to a BEREICH / OL row.
    // The picker output was validated above, so any other miss is a hard contract violation (400).
    Optional<Squadron> sq = squadronRepository.findById(stampedOrgUnitId);
    if (sq.isPresent()) {
      return sq.get();
    }
    OrgUnit specialCommand =
        specialCommandRepository.findById(stampedOrgUnitId).map(s -> (OrgUnit) s).orElse(null);
    if (specialCommand != null) {
      return specialCommand;
    }
    return orgUnitRepository
        .findById(stampedOrgUnitId)
        .filter(
            ou ->
                ou.getKind() == OrgUnitKind.BEREICH
                    || ou.getKind() == OrgUnitKind.ORGANISATIONSLEITUNG)
        .orElseThrow(
            () ->
                new BadRequestException(
                    "Picked owner org unit no longer resolves — repository miss"));
  }

  /**
   * {@code true} iff the current principal may see data owned by {@code squadronId} — where the id
   * may name either a Staffel or a Spezialkommando. Evaluates the very same effective-scope vector
   * the staffel-scoped <em>list</em> queries use ({@link #currentScopePredicate()} → {@link
   * ScopePredicate#permits(UUID)}), so a per-row detail/edit check can never diverge from what the
   * lists show:
   *
   * <ul>
   *   <li>Admin without an active pin: {@code true} for every org unit.
   *   <li>Admin or non-admin pinned to one org unit: {@code true} only for that pinned id.
   *   <li>Non-admin without a pin: {@code true} for any org unit they are a member of — the union
   *       of their Staffel <em>and</em> every Spezialkommando they belong to.
   *   <li>Anonymous: {@code false} for everything (only Mission's {@code isInternal = false} public
   *       escape, applied by the callers, lets a guest through).
   * </ul>
   *
   * <p>Before this delegated to {@link #currentScopePredicate()} it consulted only the home Staffel
   * ({@code readPersistentSquadronFromUser()}), which denied SK members — and squadron-less SK
   * leads entirely — detail/edit access to their own SK's strict aggregates and internal missions,
   * even though the lists (which already used the predicate) showed those rows. Strict-staffel
   * isolation is preserved: a non-admin still matches only org units in their own membership set,
   * and a foreign pin collapses to that set rather than granting foreign access.
   *
   * @param squadronId the org-unit id (Staffel or Spezialkommando) whose data the caller wants to
   *     read; never {@code null}.
   * @return {@code true} iff the caller may see the given org unit's data.
   */
  public boolean canSeeSquadron(@NotNull UUID squadronId) {
    return currentScopePredicate().permits(squadronId);
  }

  /**
   * Plan-aligned alias for {@link #canSeeSquadron(UUID)} — same semantics, generalised name so R2.d
   * can migrate the SpEL strings onto an org-unit-shaped vocabulary without changing behaviour.
   * Once Spezialkommando ids start flowing through the admin switcher, this method's implementation
   * will move ahead of the legacy {@code canSeeSquadron} and the latter will start delegating in
   * the opposite direction.
   *
   * @param orgUnitId the org-unit id whose data the caller wants to read; never {@code null}.
   * @return {@code true} iff the caller may see the given org unit's data.
   */
  public boolean canSeeOrgUnit(@NotNull UUID orgUnitId) {
    return canSeeSquadron(orgUnitId);
  }

  /**
   * {@code true} iff the current principal may write to data owned by {@code squadronId}. Identical
   * rule to {@link #canSeeSquadron(UUID)} — write access tracks read access for the staffel-scoped
   * aggregates. Kept as a separate method so future read/write divergence (e.g. a read-only viewer
   * role) can land here without breaking existing call sites.
   *
   * @param squadronId the squadron whose data the caller wants to write; never {@code null}.
   * @return {@code true} iff the caller may write to the given squadron's data.
   */
  public boolean canEditSquadron(@NotNull UUID squadronId) {
    return canSeeSquadron(squadronId);
  }

  /**
   * Plan-aligned alias for {@link #canEditSquadron(UUID)}; pairs with {@link #canSeeOrgUnit(UUID)}
   * the same way the legacy pair does.
   *
   * @param orgUnitId the org-unit id whose data the caller wants to write; never {@code null}.
   * @return {@code true} iff the caller may write to the given org unit's data.
   */
  public boolean canEditOrgUnit(@NotNull UUID orgUnitId) {
    return canEditSquadron(orgUnitId);
  }

  /**
   * SPEZIALKOMMANDO_PLAN.md §6.1 contextual-authority check. Returns {@code true} iff the current
   * authenticated principal carries an {@link
   * de.greluc.krt.profit.basetool.backend.support.OrgUnitContextualAuthority} matching {@code
   * (roleName, orgUnitId)}. Admins always pass — they have implicit elevated access in every
   * OrgUnit (mirrors the {@link #canEditSquadron} short-circuit).
   *
   * <p>Designed for {@code @PreAuthorize} SpEL where the OrgUnit id is only known at runtime (from
   * a request DTO field, a path variable, etc.). Example:
   *
   * <pre>{@code
   * @PreAuthorize("@ownerScopeService.hasRoleInOrgUnit(#dto.owningOrgUnitId, 'LOGISTICIAN')")
   * public InventoryItemDto createInventoryItem(@Valid @RequestBody InventoryItemCreateDto dto)
   * }</pre>
   *
   * <p>The dual-track migration: the JWT converter emits both the flat {@code ROLE_LOGISTICIAN}
   * (back-compat for existing {@code hasRole('LOGISTICIAN')} gates) and the contextual {@code
   * ROLE_LOGISTICIAN@<uuid>}. This helper matches against the contextual surface; flat-role gates
   * keep working through {@code hasRole(...)} unchanged.
   *
   * @param orgUnitId the OrgUnit the caller wants to act on; never {@code null}. {@code null}
   *     OrgUnit id is a programming error — use {@link #canEditSquadron(UUID)} for the "any
   *     OrgUnit" semantics, this method is exclusively contextual.
   * @param roleName the role to check for. Standard values today: {@code "LOGISTICIAN"}, {@code
   *     "MISSION_MANAGER"}. Never {@code null}.
   * @return {@code true} iff the caller is an admin or holds the contextual authority.
   */
  public boolean hasRoleInOrgUnit(@NotNull UUID orgUnitId, @NotNull String roleName) {
    if (authHelper.isAdmin()) {
      return true;
    }
    Optional<org.springframework.security.core.Authentication> authentication =
        authHelper.currentAuthentication();
    if (authentication.isEmpty()
        || !authentication.get().isAuthenticated()
        || authentication.get().getAuthorities() == null) {
      return false;
    }
    de.greluc.krt.profit.basetool.backend.support.OrgUnitContextualAuthority target =
        new de.greluc.krt.profit.basetool.backend.support.OrgUnitContextualAuthority(
            roleName, orgUnitId);
    for (org.springframework.security.core.GrantedAuthority a :
        authentication.get().getAuthorities()) {
      if (a instanceof de.greluc.krt.profit.basetool.backend.support.OrgUnitContextualAuthority ctx
          && ctx.equals(target)) {
        return true;
      }
    }
    return false;
  }

  /**
   * {@code true} iff the current principal may read mission {@code missionId}. Combines the generic
   * {@link #canSeeSquadron(UUID)} check with Mission's cross-staffel-visibility rule
   * (MULTI_SQUADRON_PLAN.md section 1): non-internal missions are visible from any squadron,
   * internal missions only from the owning squadron and admins. Non-existent ids return {@code
   * false}.
   *
   * <p>Audit hardenings on top of the cross-staffel rule:
   *
   * <ul>
   *   <li><b>M-2</b>: anonymous callers do not see {@code COMPLETED} / {@code CANCELLED} missions
   *       at all. The mission archive is restricted to authenticated members so a guest cannot
   *       (re-)write the participant list / finance ledger of an already-archived mission.
   *   <li><b>M-3</b>: walks the {@code parent} chain — a sub-mission with {@code isInternal=false}
   *       below an {@code isInternal=true} parent does not leak the parent's existence to anonymous
   *       callers. If ANY ancestor is internal-and-foreign, access is denied.
   * </ul>
   *
   * @param missionId mission to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the mission.
   */
  public boolean canSeeMission(@NotNull UUID missionId) {
    return missionRepository
        .findById(missionId)
        .map(
            m -> {
              if (!authHelper.isAuthenticated()
                  && ("COMPLETED".equals(m.getStatus()) || "CANCELLED".equals(m.getStatus()))) {
                return false;
              }
              for (Mission ancestor = m; ancestor != null; ancestor = ancestor.getParent()) {
                if (!canSeeMissionRow(ancestor)) {
                  return false;
                }
              }
              return true;
            })
        .orElse(false);
  }

  /**
   * Per-row visibility check shared by {@link #canSeeMission(UUID)} and its parent-chain walk.
   *
   * <ul>
   *   <li><b>Ownerless mission</b> ({@code owningOrgUnit == null}, a leadership / "Bereichsleitung"
   *       mission created by a user who belongs to no OrgUnit): visible to everyone when
   *       non-internal (the public default), and to organisation members-or-above ({@link
   *       AuthHelperService#isMemberOrAbove()}, which reaches admins) when internal. An internal
   *       ownerless mission is thus hidden from guests and anonymous visitors — the membershipless
   *       analogue of a Staffel-internal mission being visible to that Staffel.
   *   <li><b>Org-owned mission</b>: visible if the caller may see the owning org unit, or the
   *       mission is explicitly non-internal (the cross-staffel public escape).
   * </ul>
   */
  private boolean canSeeMissionRow(Mission m) {
    if (m.getOwningOrgUnit() == null) {
      if (!Boolean.TRUE.equals(m.getIsInternal())) {
        return true;
      }
      return authHelper.isMemberOrAbove();
    }
    if (canSeeSquadron(m.getOwningOrgUnit().getId())) {
      return true;
    }
    return !Boolean.TRUE.equals(m.getIsInternal());
  }

  /**
   * {@code true} iff the current principal may edit mission {@code missionId}. Strict
   * owning-squadron check — {@link #canSeeMission(UUID)}'s public-mission escape clause does NOT
   * apply to write operations (editing/finalising is the owning squadron's prerogative).
   * Non-existent ids return {@code false}.
   *
   * <p>An <b>ownerless mission</b> ({@code owningOrgUnit == null} — a leadership /
   * "Bereichsleitung" mission) has no owning org unit to scope against, so this per-row check is a
   * no-op and returns {@code true}; the effective write gate is then {@code
   * MissionSecurityService.canManageMission}'s usual elevated-role-or-owner/manager check (owner,
   * co-managers, mission-managers/officers, admins) — the same path as a normal mission, minus the
   * squadron-scope narrowing.
   *
   * @param missionId mission to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the mission.
   */
  public boolean canEditMission(@NotNull UUID missionId) {
    return missionRepository
        .findById(missionId)
        .map(m -> m.getOwningOrgUnit() == null || canEditSquadron(m.getOwningOrgUnit().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may read job order {@code jobOrderId} (Phase 3, #343).
   * Job Orders are a <em>conditionally</em> staffel-scoped aggregate:
   *
   * <ul>
   *   <li>responsible = Spezialkommando → <b>public</b>: visible to every <em>profit-eligible</em>
   *       caller (see {@link #canViewJobOrders()}), so the central SK queue stays a shared
   *       workspace across all profit squadrons. A non-profit member sees it no more than the rest
   *       of the order area.
   *   <li>responsible = Squadron → <b>private</b>: visible only to a member of that squadron and to
   *       admins. The requester does NOT grant visibility (a squadron-private order is invisible to
   *       the customer squadron unless it happens to also be the responsible one).
   * </ul>
   *
   * <p>Both branches are additionally gated by the viewer-side profit check: a caller who belongs
   * to no profit-eligible org unit (and is not an admin) may read no order at all.
   *
   * <p>A {@code null} responsible org unit (legacy rows before the V130 backfill) is treated as
   * visible — defensive only; the backfill + NOT NULL constraint means no such row survives in
   * practice. Non-existent ids return {@code false}.
   *
   * @param jobOrderId job order to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the order.
   */
  public boolean canSeeJobOrder(@NotNull UUID jobOrderId) {
    return jobOrderRepository.findById(jobOrderId).map(this::canSeeJobOrderRow).orElse(false);
  }

  /**
   * Entity overload of {@link #canSeeJobOrder(UUID)} for callers that already hold a managed {@link
   * JobOrder} (e.g. the active-order lookup projection, which loads the rows in one query) — avoids
   * a per-row {@code findById} re-fetch. Same visibility contract as the id overload (viewer-side
   * profit gate, SK-public escape, squadron-private otherwise).
   *
   * @param order the job order to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the order.
   */
  public boolean canSeeJobOrder(@NotNull JobOrder order) {
    return canSeeJobOrderRow(order);
  }

  /**
   * Per-row read check shared by {@link #canSeeJobOrder(UUID)}. First applies the viewer-side
   * profit gate ({@link #canViewJobOrders()}): a caller who is not a member of any profit-eligible
   * org unit (and is not an admin) may see no order at all — not even the otherwise-public SK
   * queue. For a permitted viewer, SK-responsible orders are public and squadron-responsible orders
   * defer to {@link #canSeeSquadron(UUID)}.
   *
   * @param o the job order whose responsible org unit gates visibility.
   * @return {@code true} iff the caller may read the row.
   */
  private boolean canSeeJobOrderRow(JobOrder o) {
    if (!canViewJobOrders()) {
      return false;
    }
    OrgUnit responsible = o.getResponsibleOrgUnit();
    if (responsible == null || responsible.getKind() == OrgUnitKind.SPECIAL_COMMAND) {
      return true;
    }
    return canSeeSquadron(responsible.getId());
  }

  /**
   * {@code true} iff the current principal may see the item job-order <em>blueprint-coverage</em>
   * view of {@code jobOrderId} — who among the order's responsible (processing) squadron/SK owns
   * the blueprints for the order's required items. This is <strong>stricter</strong> than {@link
   * #canSeeJobOrder(UUID)}: an SK-responsible order is publicly readable (its detail page is shown
   * to every profit-eligible member), but the coverage view exposes which named members hold which
   * blueprints, so it is restricted to members of the responsible org unit itself.
   *
   * <p>The check delegates to {@link #canSeeSquadron(UUID)} on the order's responsible org unit,
   * which evaluates the same effective-scope vector the staffel-scoped lists use: a non-admin
   * matches only org units in their own membership set (whether the responsible unit is a Staffel
   * or a Spezialkommando), an admin without an active pin matches every org unit, and an admin
   * pinned to another org unit does not. There is therefore no SK-public escape here — a non-member
   * viewing an SK order's detail page is denied the coverage view (HTTP 403), and the frontend
   * simply omits the section. A {@code null} responsible org unit (legacy pre-backfill rows) and
   * non-existent ids return {@code false}.
   *
   * @param jobOrderId the job order whose blueprint-coverage view the caller wants to read; never
   *     {@code null}.
   * @return {@code true} iff the caller is a member of the order's responsible org unit (or an
   *     admin with matching scope).
   */
  public boolean canSeeJobOrderBlueprintOwners(@NotNull UUID jobOrderId) {
    return jobOrderRepository
        .findById(jobOrderId)
        .map(JobOrder::getResponsibleOrgUnit)
        .map(responsible -> canSeeSquadron(responsible.getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may edit job order {@code jobOrderId} (Phase 3, #343).
   * Mirrors {@link #canSeeJobOrder(UUID)} but for writes:
   *
   * <ul>
   *   <li>responsible = Spezialkommando → editable by anyone the endpoint's role gate admits
   *       (LOGISTICIAN+). The central SK queue is a shared workspace, so write access is governed
   *       by role rather than by squadron scope; this method does not further restrict it.
   *   <li>responsible = Squadron → editable only by a member of that squadron and admins, exactly
   *       like {@link #canEditSquadron(UUID)}.
   * </ul>
   *
   * <p>Both branches are additionally gated by the viewer-side profit check ({@link
   * #canViewJobOrders()}): a caller who belongs to no profit-eligible org unit (and is not an
   * admin) may edit no order, mirroring the read path.
   *
   * <p>Non-existent ids return {@code false}.
   *
   * @param jobOrderId job order to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the order.
   */
  public boolean canEditJobOrder(@NotNull UUID jobOrderId) {
    return jobOrderRepository.findById(jobOrderId).map(this::canEditJobOrderRow).orElse(false);
  }

  /**
   * Per-row write check shared by {@link #canEditJobOrder(UUID)}. First applies the same
   * viewer-side profit gate as the read path ({@link #canViewJobOrders()}): a caller who is not a
   * member of any profit-eligible org unit (and is not an admin) may edit no order at all — not
   * even the shared SK queue. For a permitted caller, SK-responsible orders are open to the role
   * gate and squadron-responsible orders defer to {@link #canEditSquadron(UUID)}.
   *
   * @param o the job order whose responsible org unit gates write access.
   * @return {@code true} iff the caller may edit the row.
   */
  private boolean canEditJobOrderRow(JobOrder o) {
    if (!canViewJobOrders()) {
      return false;
    }
    OrgUnit responsible = o.getResponsibleOrgUnit();
    if (responsible == null || responsible.getKind() == OrgUnitKind.SPECIAL_COMMAND) {
      return true;
    }
    return canEditSquadron(responsible.getId());
  }

  /**
   * Read/write access check for an <em>ownerless personal aggregate</em> row — a ship, refinery
   * order, or inventory item whose {@code owningOrgUnit} is {@code null} because the creating user
   * belongs to no org unit (see {@link #resolveOrgUnitForPickerOutputNullable(User, UUID)}). Such a
   * row has no org-unit scope to match, so it is reachable only by:
   *
   * <ul>
   *   <li>an admin in all-scopes mode (no active pin) — mirrors the {@code isAdminAllScope}
   *       short-circuit in the list queries, so a row an admin sees in the list stays openable;
   *   <li>its own owning user — identified by comparing the row's per-user owner against the JWT
   *       {@code sub}, which is the {@code app_user} primary key, so {@link
   *       AuthHelperService#currentUserId()} compares directly against {@code owner.getId()}.
   * </ul>
   *
   * <p>An admin <em>with</em> an active pin is treated like any scoped caller and does NOT see
   * ownerless rows — consistent with {@link #canSeeSquadron(UUID)} returning {@code false} for a
   * pinned admin against a non-matching scope, and with the list queries excluding null-owner rows
   * once {@code activeOrgUnitId} is set.
   *
   * @param owner the row's per-user owner ({@code ship.owner} / {@code refinery_order.owner} /
   *     {@code inventory_item.user}); may be {@code null} defensively, which denies all non-admin
   *     access.
   * @return {@code true} iff the current caller may see/edit the ownerless personal row.
   */
  private boolean canAccessOwnerlessPersonalRow(@Nullable User owner) {
    if (authHelper.isAdmin() && readActiveSquadronFromHeader().isEmpty()) {
      return true;
    }
    return isCurrentUserOwner(owner);
  }

  /**
   * {@code true} iff the current authenticated principal is the per-user owner of the row carrying
   * {@code owner}. The JWT {@code sub} is the {@code app_user} primary key, so {@link
   * AuthHelperService#currentUserId()} compares directly against {@code owner.getId()}. An
   * anonymous caller (no {@code currentUserId}) and a {@code null} / id-less owner never match.
   *
   * @param owner the row's per-user owner ({@code inventory_item.user}, {@code ship.owner}, {@code
   *     refinery_order.owner}); may be {@code null}, which never matches.
   * @return {@code true} iff the caller is that owner.
   */
  private boolean isCurrentUserOwner(@Nullable User owner) {
    return owner != null
        && owner.getId() != null
        && authHelper.currentUserId().map(uid -> uid.equals(owner.getId())).orElse(false);
  }

  /**
   * {@code true} iff the current principal may read inventory item {@code itemId} directly (the
   * Lager-direct path — NOT the Job-Order-Kontext path, which is ungated by design). Resolution
   * order:
   *
   * <ul>
   *   <li><b>Owner escape (REQ-ORG-011)</b>: the item's per-user owner ({@code
   *       inventory_item.user}) may always read it, regardless of the org-unit stamp — even after
   *       they switch org units or lose their last membership while the row is still stamped to an
   *       org unit. This mirrors the service layer, which gates every owner action on {@code
   *       item.user == currentUser} with no org-unit narrowing, so the gate never denies what the
   *       service would allow.
   *   <li>otherwise, for an ownerless personal item ({@code owningOrgUnit == null}) it defers to
   *       {@link #canAccessOwnerlessPersonalRow(User)} (admins in all-scopes mode);
   *   <li>otherwise the strict owning-org-unit scope check ({@link #canSeeSquadron(UUID)}).
   * </ul>
   *
   * <p>Non-existent ids return {@code false}.
   *
   * @param itemId inventory item to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the item.
   */
  public boolean canSeeInventoryItem(@NotNull UUID itemId) {
    return inventoryItemRepository
        .findById(itemId)
        .map(
            i ->
                isCurrentUserOwner(i.getUser())
                    || (i.getOwningOrgUnit() == null
                        ? canAccessOwnerlessPersonalRow(i.getUser())
                        : canSeeSquadron(i.getOwningOrgUnit().getId())))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may edit inventory item {@code itemId} directly.
   * Resolution order:
   *
   * <ul>
   *   <li><b>Owner escape (REQ-ORG-011)</b>: the item's per-user owner ({@code
   *       inventory_item.user}) may always edit it, regardless of the org-unit stamp — even after
   *       they switch org units or lose their last membership while the row is still stamped to an
   *       org unit. This mirrors the service-layer owner check ({@code InventoryItemService} gates
   *       owner book-out / note / delivered / association writes on {@code item.user ==
   *       currentUser} with no org-unit narrowing), so the {@code @PreAuthorize} gate never denies
   *       what the service would allow.
   *   <li>otherwise, for an ownerless personal item ({@code owningOrgUnit == null}) it defers to
   *       {@link #canAccessOwnerlessPersonalRow(User)} (admins in all-scopes mode);
   *   <li>otherwise the strict owning-org-unit scope check ({@link #canEditSquadron(UUID)}) —
   *       Job-Order-Kontext handover writes are gated separately by {@code
   *       JobOrderHandoverService}'s {@code item.jobOrderId == currentOrder.id} guard.
   * </ul>
   *
   * <p>Non-existent ids return {@code false}.
   *
   * @param itemId inventory item to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the item.
   */
  public boolean canEditInventoryItem(@NotNull UUID itemId) {
    return inventoryItemRepository
        .findById(itemId)
        .map(
            i ->
                isCurrentUserOwner(i.getUser())
                    || (i.getOwningOrgUnit() == null
                        ? canAccessOwnerlessPersonalRow(i.getUser())
                        : canEditSquadron(i.getOwningOrgUnit().getId())))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may read refinery order {@code orderId}. The per-user
   * owner escape (REQ-ORG-011) applies first — the order's {@code refinery_order.owner} may always
   * read it regardless of the org-unit stamp; otherwise an ownerless personal order ({@code
   * owningOrgUnit == null}) defers to {@link #canAccessOwnerlessPersonalRow(User)} (admins in
   * all-scopes mode) and an org-owned order to the strict owning-org-unit check ({@link
   * #canSeeSquadron(UUID)}; refinery is a strict-staffel aggregate without a public escape).
   * Non-existent ids return {@code false}.
   *
   * @param orderId refinery order to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the order.
   */
  public boolean canSeeRefineryOrder(@NotNull UUID orderId) {
    return refineryOrderRepository.findById(orderId).map(this::canSeeRefineryOrder).orElse(false);
  }

  /**
   * Entity overload of {@link #canSeeRefineryOrder(UUID)} for callers that already hold a managed
   * {@link RefineryOrder} (e.g. the mission-scoped refinery list) — avoids a per-row {@code
   * findById} re-fetch. Same resolution as the id overload: per-user owner escape (REQ-ORG-011)
   * first, then the ownerless ({@link #canAccessOwnerlessPersonalRow(User)}) / strict
   * owning-org-unit ({@link #canSeeSquadron(UUID)}) branches.
   *
   * @param order the refinery order to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the order.
   */
  public boolean canSeeRefineryOrder(@NotNull RefineryOrder order) {
    return isCurrentUserOwner(order.getOwner())
        || (order.getOwningOrgUnit() == null
            ? canAccessOwnerlessPersonalRow(order.getOwner())
            : canSeeSquadron(order.getOwningOrgUnit().getId()));
  }

  /**
   * {@code true} iff the current principal may edit refinery order {@code orderId}. The per-user
   * owner escape (REQ-ORG-011) applies first — the order's {@code refinery_order.owner} may always
   * edit it regardless of the org-unit stamp, mirroring {@code
   * RefineryOrderService.updateRefineryOrder} / {@code #deleteRefineryOrder} / {@code
   * #storeRefineryOrder}, which authorise the owner with no org-unit narrowing — so the
   * {@code @PreAuthorize} gate never denies a write the service would accept. Otherwise an
   * ownerless personal order ({@code owningOrgUnit == null}) defers to {@link
   * #canAccessOwnerlessPersonalRow(User)} (admins in all-scopes mode) and an org-owned order to the
   * strict owning-org-unit check ({@link #canEditSquadron(UUID)}). Non-existent ids return {@code
   * false}.
   *
   * @param orderId refinery order to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the order.
   */
  public boolean canEditRefineryOrder(@NotNull UUID orderId) {
    return refineryOrderRepository
        .findById(orderId)
        .map(
            o ->
                isCurrentUserOwner(o.getOwner())
                    || (o.getOwningOrgUnit() == null
                        ? canAccessOwnerlessPersonalRow(o.getOwner())
                        : canEditSquadron(o.getOwningOrgUnit().getId())))
        .orElse(false);
  }

  /**
   * {@code true} iff the caller may read <em>any</em> of the target user's refinery orders through
   * the per-user list endpoint {@code GET /api/v1/refinery-orders/users/{userId}}. This is a
   * <b>coarse user-level pre-check</b>, not a per-row gate: it passes for an admin, the target user
   * themselves, or a caller whose strict org-unit scope ({@link #canSeeSquadron(UUID)}) covers
   * <em>any one</em> of the target user's memberships. A non-existent / membership-less target
   * yields {@code false} for non-admins.
   *
   * <p><b>Per-row scoping is NOT done here</b> (finding SEC-01). Because a member may belong to up
   * to two Staffeln (REQ-ORG-017), a caller who shares only one of them passes this {@code
   * anyMatch} gate yet must not see the target's orders stamped to the <em>other</em> Staffel —
   * which the per-order {@link #canSeeRefineryOrder(RefineryOrder)} gate would individually deny.
   * That strict-staffel filtering is enforced by the scoped list query {@code
   * RefineryOrderRepository#findByOwnerIdScoped} (via {@code
   * RefineryOrderService#getUserRefineryOrdersScoped}), so the page returned to the caller never
   * contains a row {@code canSeeRefineryOrder} would reject. This gate only decides whether the
   * caller has <em>any</em> legitimate interest in the target user at all (PR #808 / epic #800;
   * per-row leak closed by SEC-01).
   *
   * @param targetUserId the user whose refinery orders the caller wants to read; never {@code
   *     null}.
   * @return {@code true} iff the caller may read the target user's in-scope refinery orders.
   */
  public boolean canViewUserRefineryOrders(@NotNull UUID targetUserId) {
    return canActOnUserRefineryOrders(targetUserId, this::canSeeSquadron);
  }

  /**
   * Write analogue of {@link #canViewUserRefineryOrders(UUID)} for the create-on-behalf endpoint
   * {@code POST /api/v1/refinery-orders/users/{userId}}; the same coarse {@code anyMatch}
   * user-level pre-check, scoped on {@link #canEditSquadron(UUID)} instead of {@link
   * #canSeeSquadron(UUID)}. The per-row constraint that actually keeps a write in bounds is the
   * stamp validation in {@link #resolveOrgUnitForPickerOutputNullable(User, UUID)} → {@link
   * #resolveStampedOrgUnit(java.util.Set, UUID)}: the new order's {@code owningOrgUnit} must be a
   * direct membership of the target user OR a unit the caller may edit, so this gate passing on a
   * single shared unit can never let the caller stamp a row into a unit it cannot already reach.
   * Unlike the read path (SEC-01), a too-broad gate here is not a disclosure — a row stamped
   * outside the caller's scope is one the caller cannot then read back.
   *
   * @param targetUserId the user the caller wants to create a refinery order for; never {@code
   *     null}.
   * @return {@code true} iff the caller may create a refinery order on that user's behalf.
   */
  public boolean canManageUserRefineryOrders(@NotNull UUID targetUserId) {
    return canActOnUserRefineryOrders(targetUserId, this::canEditSquadron);
  }

  /**
   * Shared resolution for the per-user refinery endpoints: admin all-access, then the self escape,
   * then a coarse strict org-unit scope check against the target user's memberships (read straight
   * from {@code org_unit_membership}, never a lazy association). The {@code unitScope} predicate is
   * {@link #canSeeSquadron(UUID)} for reads / {@link #canEditSquadron(UUID)} for writes.
   *
   * <p><b>This is an {@code anyMatch} pre-check, not a per-row gate.</b> A non-admin passes as soon
   * as the caller shares <em>one</em> of the target's (up to two, REQ-ORG-017) org units, so the
   * caller may be in scope for some of the target's rows but not others. Callers MUST therefore
   * apply per-row scoping themselves: reads through the scoped query {@code findByOwnerIdScoped}
   * (SEC-01), writes through the {@link #resolveStampedOrgUnit(java.util.Set, UUID)} stamp
   * validation. This method alone does not bound which individual rows the caller may touch.
   *
   * @param targetUserId the user being acted upon; never {@code null}.
   * @param unitScope the per-unit scope check to apply; never {@code null}.
   * @return {@code true} iff the caller shares at least one in-scope org unit with the target user.
   */
  private boolean canActOnUserRefineryOrders(
      @NotNull UUID targetUserId, @NotNull java.util.function.Predicate<UUID> unitScope) {
    if (authHelper.isAdmin()) {
      return true;
    }
    if (authHelper.currentUserId().map(targetUserId::equals).orElse(false)) {
      return true;
    }
    return orgUnitMembershipRepository.findAllByIdUserId(targetUserId).stream()
        .map(m -> m.getId().getOrgUnitId())
        .anyMatch(unitScope);
  }

  /**
   * {@code true} iff the current principal may read operation {@code operationId}. Visible when
   * <em>any</em> of these holds:
   *
   * <ul>
   *   <li>the owning-squadron scope check passes (org-owned operation in the caller's scope);
   *   <li>the operation is an <em>ownerless leadership operation</em> ({@code owningOrgUnit ==
   *       null}, V145) and the caller is a member-or-above — operations have no public escape, so
   *       an ownerless operation is the org-wide analogue of a Staffel-internal operation, hidden
   *       from guests/anonymous (REQ-ORG-009);
   *   <li>the caller <em>participated</em> in one of the operation's linked missions (#500) — any
   *       authenticated participant may view the operation and their payout regardless of its
   *       owning OrgUnit. Anonymous callers never match (no {@code currentUserId}).
   * </ul>
   *
   * <p>Non-existent ids return {@code false}.
   *
   * @param operationId operation to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the operation.
   */
  public boolean canSeeOperation(@NotNull UUID operationId) {
    return operationRepository
        .findById(operationId)
        .map(
            o -> {
              boolean scopeVisible =
                  o.getOwningOrgUnit() == null
                      ? authHelper.isMemberOrAbove()
                      : canSeeSquadron(o.getOwningOrgUnit().getId());
              return scopeVisible || participatedInOperation(operationId);
            })
        .orElse(false);
  }

  /**
   * {@code true} iff the current (authenticated) caller participated in one of the operation's
   * linked missions (#500). Backs the participant-visibility escape of {@link
   * #canSeeOperation(UUID)}; an anonymous caller (no {@code currentUserId}) never participates.
   *
   * @param operationId the operation to test; never {@code null}.
   * @return {@code true} iff the caller is a participant of one of the operation's missions.
   */
  private boolean participatedInOperation(@NotNull UUID operationId) {
    return authHelper
        .currentUserId()
        .map(uid -> operationRepository.existsParticipantUserInOperation(operationId, uid))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may edit operation {@code operationId}. Strict
   * owning-squadron check for org-owned operations; for an <em>ownerless leadership operation</em>
   * ({@code owningOrgUnit == null}, V145) the per-row check is a no-op (returns {@code true}) — the
   * real write restriction is the controller's role gate ({@code hasRole('MISSION_MANAGER')} on
   * update, {@code hasRole('ADMIN')} on delete), so an org-wide leadership operation is editable by
   * any mission manager and deletable by any admin (REQ-ORG-009). Non-existent ids return {@code
   * false}.
   *
   * @param operationId operation to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the operation.
   */
  public boolean canEditOperation(@NotNull UUID operationId) {
    return operationRepository
        .findById(operationId)
        .map(o -> o.getOwningOrgUnit() == null || canEditSquadron(o.getOwningOrgUnit().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may read ship {@code shipId}. The per-user owner escape
   * (REQ-ORG-011) applies first — the ship's {@code ship.owner} may always read it regardless of
   * the org-unit stamp; otherwise an ownerless personal ship ({@code owningOrgUnit == null}) defers
   * to {@link #canAccessOwnerlessPersonalRow(User)} (admins in all-scopes mode) and an org-owned
   * ship to the strict owning-org-unit check ({@link #canSeeSquadron(UUID)}; Hangar = strict eigene
   * Staffel). Non-existent ids return {@code false}.
   *
   * @param shipId ship to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the ship.
   */
  public boolean canSeeShip(@NotNull UUID shipId) {
    return shipRepository
        .findById(shipId)
        .map(
            s ->
                isCurrentUserOwner(s.getOwner())
                    || (s.getOwningOrgUnit() == null
                        ? canAccessOwnerlessPersonalRow(s.getOwner())
                        : canSeeSquadron(s.getOwningOrgUnit().getId())))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may edit ship {@code shipId}. The per-user owner escape
   * (REQ-ORG-011) applies first — the ship's {@code ship.owner} may always edit it regardless of
   * the org-unit stamp, mirroring {@code HangarService.updateShip} / {@code #deleteShip}, which
   * reject any non-owner caller, so the {@code @PreAuthorize} gate never denies a write the service
   * would accept. Otherwise an ownerless personal ship ({@code owningOrgUnit == null}) defers to
   * {@link #canAccessOwnerlessPersonalRow(User)} (admins in all-scopes mode) and an org-owned ship
   * to the strict owning-org-unit check ({@link #canEditSquadron(UUID)}). Non-existent ids return
   * {@code false}.
   *
   * @param shipId ship to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the ship.
   */
  public boolean canEditShip(@NotNull UUID shipId) {
    return shipRepository
        .findById(shipId)
        .map(
            s ->
                isCurrentUserOwner(s.getOwner())
                    || (s.getOwningOrgUnit() == null
                        ? canAccessOwnerlessPersonalRow(s.getOwner())
                        : canEditSquadron(s.getOwningOrgUnit().getId())))
        .orElse(false);
  }

  /**
   * Reports whether the per-squadron promotion-system feature flag is on for the caller's scope.
   *
   * <ul>
   *   <li>Admin without an active pin (all-scopes mode) — {@code true}; admins keep access so they
   *       can re-enable a squadron that locked itself out without losing the menu entry.
   *   <li>Admin pinned to a squadron — uses the pinned squadron's flag so the pinned view stays
   *       consistent with what a member of that squadron would see. An admin who wants to re-enable
   *       a locked-out squadron either clears the pin first (all-scopes mode) or navigates directly
   *       to {@code /admin/settings} (not gated by this check).
   *   <li>Non-admin with an effective squadron — returns the flag stored on that squadron's row.
   *   <li>Caller without an effective squadron (anonymous / member without squadron) — {@code
   *       true}, since the squadron-scope filter already returns empty lists for them.
   * </ul>
   *
   * <p>The pin-awareness comes from {@link #currentSquadron()}, which already resolves an admin's
   * pin from the request header and a non-admin's home squadron from the membership row. The
   * earlier blanket admin bypass was dropped because it broke the pinned-view UX — see CLAUDE.md
   * "Multi-squadron tenancy" for the updated semantics.
   *
   * @return {@code true} when the promotion menu may be exposed for the caller.
   */
  public boolean isPromotionFeatureEnabledForCurrentScope() {
    return currentSquadron().map(Squadron::isPromotionEnabled).orElse(true);
  }

  /**
   * {@code true} iff the current caller may read <em>any</em> promotion data. Promotion is
   * per-squadron, so a caller needs either the elevated all-squadrons view (admin) or an effective
   * home squadron to see a system at all. A non-admin without any squadron membership has no
   * promotion system of their own; every promotion list / eligibility read must short-circuit to
   * empty for them rather than fall through to the {@code null}-scope cross-squadron union that
   * admins rely on. Detail reads are already covered by {@link #canSeeSquadron(UUID)} (which
   * returns {@code false} for a squadron-less non-admin), so this guard is only needed on the
   * {@code null}-means-all list / eligibility paths.
   *
   * @return {@code true} for admins (all-scopes or pinned) and for non-admins with an effective
   *     squadron; {@code false} for a squadron-less non-admin or anonymous caller.
   */
  public boolean hasPromotionReadAccess() {
    return authHelper.isAdmin() || currentSquadronId().isPresent();
  }

  /**
   * Throws {@link AccessDeniedException} when the per-squadron promotion-system feature flag is off
   * for the caller's scope. Admins bypass the check (see {@link
   * #isPromotionFeatureEnabledForCurrentScope()} for the resolution rules). Used at the top of
   * every promotion write-service method to short-circuit the request with HTTP 403 before any
   * mutation runs.
   *
   * @throws AccessDeniedException if a non-admin caller's home squadron has the flag disabled.
   */
  public void assertPromotionFeatureEnabled() {
    if (!isPromotionFeatureEnabledForCurrentScope()) {
      throw new AccessDeniedException(
          "Promotion feature is disabled for the caller's squadron; ask an administrator to"
              + " re-enable it.");
    }
  }

  @NotNull
  private Optional<UUID> readActiveSquadronFromHeader() {
    return parseHeaderUuid(request.getHeader(ACTIVE_ORG_UNIT_HEADER));
  }

  /**
   * Parses a single header value into a UUID. Returns {@link Optional#empty()} on {@code null},
   * blank, or malformed input. Malformed input is debug-logged inside the caller rather than thrown
   * so a stray client cannot spam the WARN channel.
   *
   * @param raw raw header value from {@link HttpServletRequest#getHeader(String)}; may be {@code
   *     null}.
   * @return parsed UUID or empty.
   */
  @NotNull
  private static Optional<UUID> parseHeaderUuid(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(raw.trim()));
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
  }

  @NotNull
  private Optional<UUID> readPersistentSquadronFromUser() {
    Optional<Optional<UUID>> cached = readCachedOptional(CACHE_KEY_PERSISTENT_USER_SQUADRON_ID);
    if (cached.isPresent()) {
      return cached.get();
    }
    // REQ-ORG-017: the user's Staffel lives in org_unit_membership (kind=SQUADRON), and a user may
    // now hold up to TWO Staffeln (the V98 uq_org_unit_membership_one_squadron index was relaxed to
    // <=2 in V164). This single-valued accessor resolves the caller's ACTIVE Staffel: honour an
    // X-Active-Org-Unit-Id pin that points at one of the caller's own Staffel memberships
    // (mirroring
    // the non-admin pin handling in currentScopePredicate()); otherwise fall back to a
    // DETERMINISTIC
    // name-sorted primary (matching UserMapper.resolveSquadron / UserDto.squadron) rather than an
    // arbitrary first row, so the auto-stamp and single-value surfaces agree with the displayed
    // primary Staffel.
    Optional<UUID> resolved =
        authHelper
            .currentUserId()
            .flatMap(
                userId -> {
                  List<OrgUnitMembership> rows =
                      orgUnitMembershipRepository.findAllByIdUserIdAndKind(
                          userId, OrgUnitKind.SQUADRON);
                  if (rows.isEmpty()) {
                    return Optional.empty();
                  }
                  Optional<UUID> pinned = readActiveSquadronFromHeader();
                  if (pinned.isPresent()
                      && rows.stream()
                          .anyMatch(r -> r.getId().getOrgUnitId().equals(pinned.get()))) {
                    return pinned;
                  }
                  // No matching pin: the deterministic name-sorted primary. The name-sort (and the
                  // single-Staffel fast path that skips the squadron load) is owned by
                  // StaffelMembershipResolver so this fallback agrees with UserDto.squadron /
                  // OrgUnitMembershipService.findStaffelMembershipOrgUnitIds by construction.
                  return staffelMembershipResolver.resolveNameSortedStaffelIds(rows).stream()
                      .findFirst();
                });
    request.setAttribute(CACHE_KEY_PERSISTENT_USER_SQUADRON_ID, resolved);
    return resolved;
  }

  /**
   * Reads a previously-cached {@link Optional} from the current {@link HttpServletRequest} under
   * {@code key}. The outer {@link Optional} of the return value signals presence in the cache: an
   * outer {@link Optional#empty()} means "key not yet written, do the real work", while a present
   * outer Optional wraps the cached value (which may itself be {@link Optional#empty()} for the
   * "resolved-to-empty" case). Keeps the unchecked cast confined to a single helper instead of
   * being repeated at every call site.
   *
   * @param key request-attribute key under which the cached Optional was previously stored.
   * @param <T> element type of the cached Optional.
   * @return outer-present iff the key has been written this request; the inner Optional is the
   *     cached value as written.
   */
  @NotNull
  @SuppressWarnings("unchecked")
  private <T> Optional<Optional<T>> readCachedOptional(@NotNull String key) {
    Object raw = request.getAttribute(key);
    if (raw instanceof Optional<?> opt) {
      return Optional.of((Optional<T>) opt);
    }
    return Optional.empty();
  }
}
