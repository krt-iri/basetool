package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.OrgUnit;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.SpecialCommandRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
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
 *   <li>For an admin, the {@link #ACTIVE_SQUADRON_HEADER} request header relayed by the frontend's
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
   * Name of the HTTP request header through which the frontend relays the admin's active squadron
   * selection. A {@code null}/missing value means "no active selection" (admin sees all squadrons);
   * a non-blank UUID restricts the admin to that org unit's data for the duration of this request.
   * Source of truth lives on the frontend (Redis-backed Spring Session via the {@code
   * MeFrontendController}); the backend treats the header as untrusted-but-bounded input — only
   * honoured for principals that already carry {@code ROLE_ADMIN}.
   *
   * <p>Header name is preserved verbatim from the {@code SquadronScopeService} era — R2.d / R3 may
   * rename it to {@code X-Active-Org-Unit-Id} once the frontend's relay filter and the admin's
   * existing cached browser tabs have been migrated. Keeping the legacy name in R2.c avoids
   * breaking any admin session that is open when the deploy lands.
   */
  public static final String ACTIVE_SQUADRON_HEADER = "X-Active-Squadron-Id";

  /**
   * Plan §7.2 / R5.e replacement for {@link #ACTIVE_SQUADRON_HEADER}. The frontend's relay filter
   * sends this name on every backend call once R5.e is deployed; the legacy {@link
   * #ACTIVE_SQUADRON_HEADER} name remains accepted as an alias for one release so admin browser
   * tabs that were open during deploy keep working. {@link #readActiveSquadronFromHeader()} reads
   * the new name first, falls back to the legacy one if missing — once the soak window closes and
   * the legacy header has not been seen in prod logs for a release cycle, the fallback branch and
   * the legacy constant come out.
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
  private final HttpServletRequest request;

  /**
   * Returns the org-unit context that filters the current request. For admins this reads the {@code
   * X-Active-Squadron-Id} request header (the frontend's switcher pushed there via the relay
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
   *       the same X-Active-Squadron-Id header that admins use today.
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
    // header carries that selection. The pin is only honoured when it points to one of the
    // caller's actual memberships (defence against a spoofed header from a curl call); a foreign
    // pin silently collapses to the membership-union read so the user never sees data they did
    // not opt into.
    java.util.Set<UUID> memberOrgUnitIds = currentMemberOrgUnitIds();
    Optional<UUID> pinned = readActiveSquadronFromHeader();
    if (pinned.isPresent() && memberOrgUnitIds.contains(pinned.get())) {
      return new ScopePredicate(false, pinned.get(), java.util.Set.of());
    }
    return new ScopePredicate(false, null, memberOrgUnitIds);
  }

  /**
   * Helper for {@link #currentScopePredicate()}: resolves every OrgUnit id the current non-admin
   * caller is a member of. Used by the union-of-memberships branch of the scope predicate. Returns
   * the empty set for anonymous callers and (technically) for admins, although the latter never
   * reaches this method through {@link #currentScopePredicate()}.
   *
   * <p>The result is memoised on the {@link HttpServletRequest} for the duration of the request.
   * Since the Job-Order profit gate ({@link #canViewJobOrders()}) landed, the resolver is consulted
   * more than once per request — the list path reads it via both {@code canViewJobOrders()} and
   * {@link #currentScopePredicate()}, and the detail/edit paths read it via {@code
   * canViewJobOrders()} — so the single cached {@code Set<UUID>} collapses what would otherwise be
   * two or three identical {@code findAllByIdUserId} queries into one.
   *
   * <p>Post-D3: every membership row (Staffel + SK) flows through {@code
   * OrgUnitMembershipRepository.findAllByIdUserId} — the legacy {@code User.squadron} column was
   * dropped in R9 Step 5 / V101.
   *
   * @return the union of OrgUnit ids the caller belongs to, never {@code null}.
   */
  @NotNull
  public java.util.Set<UUID> currentMemberOrgUnitIds() {
    Object cached = request.getAttribute(CACHE_KEY_MEMBER_ORG_UNIT_IDS);
    if (cached instanceof java.util.Set<?> set) {
      @SuppressWarnings("unchecked")
      java.util.Set<UUID> typed = (java.util.Set<UUID>) set;
      return typed;
    }
    Optional<UUID> userIdOpt = authHelper.currentUserId();
    java.util.Set<UUID> ids;
    if (userIdOpt.isEmpty()) {
      ids = java.util.Set.of();
    } else {
      ids = new java.util.LinkedHashSet<>();
      for (OrgUnitMembership m : orgUnitMembershipRepository.findAllByIdUserId(userIdOpt.get())) {
        ids.add(m.getId().getOrgUnitId());
      }
    }
    request.setAttribute(CACHE_KEY_MEMBER_ORG_UNIT_IDS, ids);
    return ids;
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
   *   <li>Spezialkommando leads — for the SK(s) they lead.
   * </ul>
   *
   * <p>A plain member, or a contextual logistician who is neither an officer nor an SK lead, is
   * rejected (no menu entry, empty / forbidden API). The lead branch scans the caller's memberships
   * for any {@code is_lead} row; the V95 CHECK constraint pins {@code is_lead} to Spezialkommando
   * memberships, so a hit means the caller leads at least one SK.
   *
   * @return {@code true} iff the caller is an admin, an officer, or the lead of at least one SK.
   */
  public boolean canAccessBlueprintOverview() {
    if (authHelper.isAdmin() || authHelper.hasReachableRole("ROLE_OFFICER")) {
      return true;
    }
    return authHelper
        .currentUserId()
        .map(
            userId ->
                orgUnitMembershipRepository.findAllByIdUserId(userId).stream()
                    .anyMatch(OrgUnitMembership::isLead))
        .orElse(false);
  }

  /**
   * #364 effective scope for the blueprint-availability overview, encoded as a {@link
   * ScopePredicate} so the aggregation reuses the same three-field shape as the staffel-scoped list
   * queries. Unlike {@link #currentScopePredicate()} — which returns the union of <em>all</em> of a
   * non-admin's memberships — this restricts a non-admin to the org units they have oversight over,
   * mirroring {@link #canAccessBlueprintOverview()}:
   *
   * <ul>
   *   <li>admin → delegates to {@link #currentScopePredicate()} (all org units, or the pinned one);
   *   <li>officer → their own Staffel (via {@link #readPersistentSquadronFromUser()});
   *   <li>SK lead → every SK they lead;
   *   <li>an active pin is honoured only when it points at one of those oversight org units,
   *       otherwise it is ignored and the full oversight union applies.
   * </ul>
   *
   * <p>A caller with an empty oversight set (e.g. a plain member who reached the service despite
   * the gate) yields {@code memberOrgUnitIds = {}}, which the aggregation treats as "no rows".
   *
   * @return a never-null scope vector of the org units whose blueprints the caller may oversee.
   */
  @NotNull
  public ScopePredicate currentBlueprintOversightScope() {
    if (authHelper.isAdmin()) {
      return currentScopePredicate();
    }
    Set<UUID> oversightOrgUnitIds = new LinkedHashSet<>();
    if (authHelper.hasReachableRole("ROLE_OFFICER")) {
      readPersistentSquadronFromUser().ifPresent(oversightOrgUnitIds::add);
    }
    authHelper
        .currentUserId()
        .ifPresent(
            userId -> {
              for (OrgUnitMembership m : orgUnitMembershipRepository.findAllByIdUserId(userId)) {
                if (m.isLead()) {
                  oversightOrgUnitIds.add(m.getId().getOrgUnitId());
                }
              }
            });
    Optional<UUID> pinned = readActiveSquadronFromHeader();
    if (pinned.isPresent() && oversightOrgUnitIds.contains(pinned.get())) {
      return new ScopePredicate(false, pinned.get(), Set.of());
    }
    return new ScopePredicate(false, null, oversightOrgUnitIds);
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
        throw new BadRequestException(
            "User belongs to multiple org units; owningOrgUnitId is required");
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
   * <p>Decision matrix matches {@link #resolveSquadronForPickerOutput(User, UUID)} byte-for-byte: 0
   * memberships → 400; 1 + null picker → auto-stamp; 1 + valid → honour; 1 + mismatch → 400; &gt;1
   * + null picker → 400 (force explicit choice); &gt;1 + valid → honour; &gt;1 + foreign → 400.
   *
   * @param targetUser the user whose memberships gate the picker output validation; never {@code
   *     null}.
   * @param owningOrgUnitId the picker-supplied org unit id; {@code null} triggers the auto-stamp
   *     path when the user has exactly one membership.
   * @return the resolved {@link OrgUnit} — either a {@link Squadron} or a {@link
   *     de.greluc.krt.iri.basetool.backend.model.SpecialCommand}; never {@code null}.
   * @throws BadRequestException per the matrix above.
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
   * SquadronRepository}, Spezialkommando via {@link SpecialCommandRepository}). Shared tail of
   * {@link #resolveOrgUnitForPickerOutput(User, UUID)} and {@link
   * #resolveOrgUnitForPickerOutputNullable(User, UUID)} — the empty-membership branch differs
   * between the two callers and is handled by each before delegating here.
   *
   * @param memberOrgUnitIds the caller's non-empty membership set.
   * @param owningOrgUnitId the picker-supplied org unit id, or {@code null} for the auto-stamp
   *     path.
   * @return the resolved {@link OrgUnit}; never {@code null}.
   * @throws BadRequestException on a foreign choice, a &gt;1-membership {@code null} choice, or a
   *     resolved id that no longer exists in either repository.
   */
  @NotNull
  private OrgUnit resolveStampedOrgUnit(@NotNull Set<UUID> memberOrgUnitIds, UUID owningOrgUnitId) {
    UUID stampedOrgUnitId;
    if (owningOrgUnitId == null) {
      if (memberOrgUnitIds.size() == 1) {
        stampedOrgUnitId = memberOrgUnitIds.iterator().next();
      } else {
        throw new BadRequestException(
            "User belongs to multiple org units; owningOrgUnitId is required");
      }
    } else {
      if (!memberOrgUnitIds.contains(owningOrgUnitId)) {
        throw new BadRequestException(
            "Selected owner org unit is not a membership of the target user");
      }
      stampedOrgUnitId = owningOrgUnitId;
    }

    // Resolve to the concrete subtype. Staffel-side: SquadronRepository (discriminator filter
    // matches). SK-side: SpecialCommandRepository. Picker output was validated against the
    // membership set above so a missing row here is a hard contract violation, surfaced as 400.
    Optional<Squadron> sq = squadronRepository.findById(stampedOrgUnitId);
    if (sq.isPresent()) {
      return sq.get();
    }
    return specialCommandRepository
        .findById(stampedOrgUnitId)
        .map(s -> (OrgUnit) s)
        .orElseThrow(
            () ->
                new BadRequestException(
                    "Picked owner org unit no longer resolves — repository miss"));
  }

  /**
   * {@code true} iff the current principal may see data owned by {@code squadronId}.
   *
   * <ul>
   *   <li>Admin without an active squadron header: always {@code true}.
   *   <li>Admin with an active squadron header: {@code true} only for the selected squadron — the
   *       admin opted into the focused view and must switch back to "all" to break out.
   *   <li>Non-admin: {@code true} only for the user's home squadron.
   * </ul>
   *
   * @param squadronId the squadron whose data the caller wants to read; never {@code null}.
   * @return {@code true} iff the caller may see the given squadron's data.
   */
  public boolean canSeeSquadron(@NotNull UUID squadronId) {
    if (authHelper.isAdmin()) {
      return readActiveSquadronFromHeader().map(active -> active.equals(squadronId)).orElse(true);
    }
    return readPersistentSquadronFromUser().map(active -> active.equals(squadronId)).orElse(false);
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
   * de.greluc.krt.iri.basetool.backend.config.OrgUnitContextualAuthority} matching {@code
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
    de.greluc.krt.iri.basetool.backend.config.OrgUnitContextualAuthority target =
        new de.greluc.krt.iri.basetool.backend.config.OrgUnitContextualAuthority(
            roleName, orgUnitId);
    for (org.springframework.security.core.GrantedAuthority a :
        authentication.get().getAuthorities()) {
      if (a instanceof de.greluc.krt.iri.basetool.backend.config.OrgUnitContextualAuthority ctx
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
   * Per-row visibility check shared by {@link #canSeeMission(UUID)} and its parent-chain walk: a
   * mission is visible iff its owning squadron is null (unscoped, legacy rows), the caller may see
   * that owning squadron, or the mission is explicitly non-internal.
   */
  private boolean canSeeMissionRow(Mission m) {
    if (m.getOwningOrgUnit() == null) {
      return true;
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
    return owner != null
        && owner.getId() != null
        && authHelper.currentUserId().map(uid -> uid.equals(owner.getId())).orElse(false);
  }

  /**
   * {@code true} iff the current principal may read inventory item {@code itemId} directly (the
   * Lager-direct path — NOT the Job-Order-Kontext path, which is ungated by design). Strict
   * owning-squadron check for org-owned items; for an ownerless personal item ({@code owningOrgUnit
   * == null}) it defers to {@link #canAccessOwnerlessPersonalRow(User)} (owner-only, plus admins in
   * all-scopes mode). Non-existent ids return {@code false}.
   *
   * @param itemId inventory item to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the item.
   */
  public boolean canSeeInventoryItem(@NotNull UUID itemId) {
    return inventoryItemRepository
        .findById(itemId)
        .map(
            i ->
                i.getOwningOrgUnit() == null
                    ? canAccessOwnerlessPersonalRow(i.getUser())
                    : canSeeSquadron(i.getOwningOrgUnit().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may edit inventory item {@code itemId} directly. Strict
   * owning-squadron check for org-owned items — Job-Order-Kontext handover writes are gated
   * separately by {@code JobOrderHandoverService}'s {@code item.jobOrderId == currentOrder.id}
   * guard. For an ownerless personal item ({@code owningOrgUnit == null}) it defers to {@link
   * #canAccessOwnerlessPersonalRow(User)} (owner-only, plus admins in all-scopes mode).
   * Non-existent ids return {@code false}.
   *
   * @param itemId inventory item to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the item.
   */
  public boolean canEditInventoryItem(@NotNull UUID itemId) {
    return inventoryItemRepository
        .findById(itemId)
        .map(
            i ->
                i.getOwningOrgUnit() == null
                    ? canAccessOwnerlessPersonalRow(i.getUser())
                    : canEditSquadron(i.getOwningOrgUnit().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may read refinery order {@code orderId}. Strict
   * owning-squadron check for org-owned orders (refinery is a strict-staffel aggregate without a
   * public escape); for an ownerless personal order ({@code owningOrgUnit == null}) it defers to
   * {@link #canAccessOwnerlessPersonalRow(User)} (owner-only, plus admins in all-scopes mode).
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
   * findById} re-fetch. Strict owning-squadron check (refinery is a strict-staffel aggregate with
   * no public escape); an ownerless personal order ({@code owningOrgUnit == null}) defers to {@link
   * #canAccessOwnerlessPersonalRow(User)}.
   *
   * @param order the refinery order to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the order.
   */
  public boolean canSeeRefineryOrder(@NotNull RefineryOrder order) {
    return order.getOwningOrgUnit() == null
        ? canAccessOwnerlessPersonalRow(order.getOwner())
        : canSeeSquadron(order.getOwningOrgUnit().getId());
  }

  /**
   * {@code true} iff the current principal may edit refinery order {@code orderId}. Strict
   * owning-squadron check for org-owned orders; for an ownerless personal order ({@code
   * owningOrgUnit == null}) it defers to {@link #canAccessOwnerlessPersonalRow(User)} (owner-only,
   * plus admins in all-scopes mode). Non-existent ids return {@code false}.
   *
   * @param orderId refinery order to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the order.
   */
  public boolean canEditRefineryOrder(@NotNull UUID orderId) {
    return refineryOrderRepository
        .findById(orderId)
        .map(
            o ->
                o.getOwningOrgUnit() == null
                    ? canAccessOwnerlessPersonalRow(o.getOwner())
                    : canEditSquadron(o.getOwningOrgUnit().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may read operation {@code operationId}. Strict
   * owning-squadron check. Non-existent ids return {@code false}.
   *
   * @param operationId operation to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the operation.
   */
  public boolean canSeeOperation(@NotNull UUID operationId) {
    return operationRepository
        .findById(operationId)
        .map(o -> o.getOwningOrgUnit() == null || canSeeSquadron(o.getOwningOrgUnit().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may edit operation {@code operationId}. Strict
   * owning-squadron check. Non-existent ids return {@code false}.
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
   * {@code true} iff the current principal may read ship {@code shipId}. Strict owning-squadron
   * check for org-owned ships (Hangar = strict eigene Staffel); for an ownerless personal ship
   * ({@code owningOrgUnit == null}) it defers to {@link #canAccessOwnerlessPersonalRow(User)}
   * (owner-only, plus admins in all-scopes mode). Non-existent ids return {@code false}.
   *
   * @param shipId ship to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the ship.
   */
  public boolean canSeeShip(@NotNull UUID shipId) {
    return shipRepository
        .findById(shipId)
        .map(
            s ->
                s.getOwningOrgUnit() == null
                    ? canAccessOwnerlessPersonalRow(s.getOwner())
                    : canSeeSquadron(s.getOwningOrgUnit().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may edit ship {@code shipId}. Strict owning-squadron
   * check for org-owned ships; for an ownerless personal ship ({@code owningOrgUnit == null}) it
   * defers to {@link #canAccessOwnerlessPersonalRow(User)} (owner-only, plus admins in all-scopes
   * mode). Non-existent ids return {@code false}.
   *
   * @param shipId ship to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the ship.
   */
  public boolean canEditShip(@NotNull UUID shipId) {
    return shipRepository
        .findById(shipId)
        .map(
            s ->
                s.getOwningOrgUnit() == null
                    ? canAccessOwnerlessPersonalRow(s.getOwner())
                    : canEditSquadron(s.getOwningOrgUnit().getId()))
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
    // R5.e: read the plan-aligned X-Active-Org-Unit-Id first; fall back to the legacy
    // X-Active-Squadron-Id alias so admin browser tabs cached against the old name during deploy
    // keep working for one release. Once the legacy header stops appearing in prod logs, the
    // fallback comes out together with the constant.
    Optional<UUID> fromNew = parseHeaderUuid(request.getHeader(ACTIVE_ORG_UNIT_HEADER));
    if (fromNew.isPresent()) {
      return fromNew;
    }
    return parseHeaderUuid(request.getHeader(ACTIVE_SQUADRON_HEADER));
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
    // Post-D3: the user's home Staffel lives in org_unit_membership (kind=SQUADRON). The V95
    // partial unique index guarantees at most one row, so a List read with an in-loop pick is the
    // right shape — Spring Data's findOneByIdUserIdAndKind would throw on the data-corruption case
    // we tolerate elsewhere.
    Optional<UUID> resolved =
        authHelper
            .currentUserId()
            .flatMap(
                userId -> {
                  List<OrgUnitMembership> rows =
                      orgUnitMembershipRepository.findAllByIdUserIdAndKind(
                          userId, OrgUnitKind.SQUADRON);
                  return rows.isEmpty()
                      ? Optional.empty()
                      : Optional.of(rows.get(0).getId().getOrgUnitId());
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
