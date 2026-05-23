package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.OrgUnit;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
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

  private final AuthHelperService authHelper;
  private final UserRepository userRepository;
  private final SquadronRepository squadronRepository;
  private final MissionRepository missionRepository;
  private final InventoryItemRepository inventoryItemRepository;
  private final RefineryOrderRepository refineryOrderRepository;
  private final OperationRepository operationRepository;
  private final ShipRepository shipRepository;
  private final OrgUnitMembershipRepository orgUnitMembershipRepository;
  private final HttpServletRequest request;

  /**
   * Returns the org-unit context that filters the current request. For admins this reads the {@code
   * X-Active-Squadron-Id} request header (the frontend's switcher pushed there via the relay
   * filter); for everyone else this loads the user's persistent home squadron. Empty result means
   * "no filter" for admins ("all squadrons") and "no access" for non-admins (typically
   * unauthenticated / anonymous).
   *
   * <p>The non-admin branch's {@code userRepository.findById} round-trip is memoised on the {@link
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
   * <p>The result is computed fresh every call — there is no per-request memoisation yet because
   * the resolver is invoked exactly once per request (via {@link #currentScopePredicate()}) and the
   * additional cache key would only pay off if the resolver was called multiple times.
   *
   * @return the union of OrgUnit ids the caller belongs to, never {@code null}.
   */
  @NotNull
  public java.util.Set<UUID> currentMemberOrgUnitIds() {
    Optional<UUID> userIdOpt = authHelper.currentUserId();
    if (userIdOpt.isEmpty()) {
      return java.util.Set.of();
    }
    Optional<User> userOpt = userRepository.findById(userIdOpt.get());
    if (userOpt.isEmpty()) {
      return java.util.Set.of();
    }
    User user = userOpt.get();
    java.util.Set<UUID> ids = new java.util.LinkedHashSet<>();
    if (user.getSquadron() != null) {
      ids.add(user.getSquadron().getId());
    }
    for (OrgUnitMembership m :
        orgUnitMembershipRepository.findAllByIdUserIdAndKind(
            user.getId(), OrgUnitKind.SPECIAL_COMMAND)) {
      ids.add(m.getId().getOrgUnitId());
    }
    return ids;
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
    Squadron homeStaffel = targetUser.getSquadron();
    if (homeStaffel != null) {
      memberOrgUnitIds.add(homeStaffel.getId());
    }
    List<OrgUnitMembership> skMemberships =
        orgUnitMembershipRepository.findAllByIdUserIdAndKind(
            targetUser.getId(), OrgUnitKind.SPECIAL_COMMAND);
    for (OrgUnitMembership m : skMemberships) {
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
    if (m.getOwningSquadron() == null) {
      return true;
    }
    if (canSeeSquadron(m.getOwningSquadron().getId())) {
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
        .map(m -> m.getOwningSquadron() == null || canEditSquadron(m.getOwningSquadron().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may read inventory item {@code itemId} directly (the
   * Lager-direct path — NOT the Job-Order-Kontext path, which is ungated by design). Strict
   * owning-squadron check. Non-existent ids return {@code false}.
   *
   * @param itemId inventory item to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the item.
   */
  public boolean canSeeInventoryItem(@NotNull UUID itemId) {
    return inventoryItemRepository
        .findById(itemId)
        .map(i -> i.getOwningSquadron() == null || canSeeSquadron(i.getOwningSquadron().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may edit inventory item {@code itemId} directly. Strict
   * owning-squadron check — Job-Order-Kontext handover writes are gated separately by {@code
   * JobOrderHandoverService}'s {@code item.jobOrderId == currentOrder.id} guard. Non-existent ids
   * return {@code false}.
   *
   * @param itemId inventory item to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the item.
   */
  public boolean canEditInventoryItem(@NotNull UUID itemId) {
    return inventoryItemRepository
        .findById(itemId)
        .map(i -> i.getOwningSquadron() == null || canEditSquadron(i.getOwningSquadron().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may read refinery order {@code orderId}. Strict
   * owning-squadron check (refinery is a strict-staffel aggregate without a public escape).
   * Non-existent ids return {@code false}.
   *
   * @param orderId refinery order to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the order.
   */
  public boolean canSeeRefineryOrder(@NotNull UUID orderId) {
    return refineryOrderRepository
        .findById(orderId)
        .map(o -> o.getOwningSquadron() == null || canSeeSquadron(o.getOwningSquadron().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may edit refinery order {@code orderId}. Strict
   * owning-squadron check. Non-existent ids return {@code false}.
   *
   * @param orderId refinery order to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the order.
   */
  public boolean canEditRefineryOrder(@NotNull UUID orderId) {
    return refineryOrderRepository
        .findById(orderId)
        .map(o -> o.getOwningSquadron() == null || canEditSquadron(o.getOwningSquadron().getId()))
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
        .map(o -> o.getOwningSquadron() == null || canSeeSquadron(o.getOwningSquadron().getId()))
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
        .map(o -> o.getOwningSquadron() == null || canEditSquadron(o.getOwningSquadron().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may read ship {@code shipId}. Strict owning-squadron
   * check (Hangar = strict eigene Staffel). Non-existent ids return {@code false}.
   *
   * @param shipId ship to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the ship.
   */
  public boolean canSeeShip(@NotNull UUID shipId) {
    return shipRepository
        .findById(shipId)
        .map(s -> s.getOwningSquadron() == null || canSeeSquadron(s.getOwningSquadron().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may edit ship {@code shipId}. Strict owning-squadron
   * check. Non-existent ids return {@code false}.
   *
   * @param shipId ship to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the ship.
   */
  public boolean canEditShip(@NotNull UUID shipId) {
    return shipRepository
        .findById(shipId)
        .map(s -> s.getOwningSquadron() == null || canEditSquadron(s.getOwningSquadron().getId()))
        .orElse(false);
  }

  /**
   * Reports whether the per-squadron promotion-system feature flag is on for the caller's scope.
   *
   * <ul>
   *   <li>Admin — always {@code true}; admins must retain access regardless of the flag so they can
   *       re-enable a squadron that locked itself out and pick up exactly where it left off.
   *   <li>Caller has an effective squadron — returns the flag stored on that squadron's row.
   *   <li>Caller has no effective squadron (unauthenticated / member without squadron) — {@code
   *       true}, since the squadron-scope filter already returns empty lists for them.
   * </ul>
   *
   * @return {@code true} when the promotion menu may be exposed for the caller.
   */
  public boolean isPromotionFeatureEnabledForCurrentScope() {
    if (authHelper.isAdmin()) {
      return true;
    }
    return currentSquadron().map(Squadron::isPromotionEnabled).orElse(true);
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
    Optional<UUID> resolved =
        authHelper
            .currentUserId()
            .flatMap(userRepository::findById)
            .map(User::getSquadron)
            .map(Squadron::getId);
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
