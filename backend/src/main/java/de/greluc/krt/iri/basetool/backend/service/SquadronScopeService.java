package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Resolves the squadron context of the current request and answers the "may the caller see / edit
 * this squadron-scoped data?" questions that gate every {@code @PreAuthorize} on the staffel-scoped
 * aggregates (mission, hangar, inventory, refinery, operation).
 *
 * <p>Two squadron contexts feed into the resolution:
 *
 * <ul>
 *   <li>For a non-admin user, the persistent {@code app_user.squadron_id} they were assigned to.
 *   <li>For an admin, the {@link #ACTIVE_SQUADRON_HEADER} request header relayed by the frontend's
 *       WebClient ({@code ActiveSquadronRelayFilter} reads the admin's choice from the frontend's
 *       Redis-backed Spring Session and attaches it to every outbound backend call). {@code
 *       null}/missing means "all squadrons" - admins are not constrained when no active selection
 *       exists. The backend deliberately does NOT keep its own session for this preference: REST
 *       calls from the frontend do not relay session cookies (only the OAuth2 bearer token), so a
 *       backend {@code HttpSession.setAttribute} would not survive between calls.
 * </ul>
 *
 * <p>The split keeps {@link AuthHelperService} narrow (its only job is being the single seam to
 * {@link org.springframework.security.core.context.SecurityContextHolder}) and concentrates the
 * tenant-awareness in one testable bean. Aggregate-specific entry points ({@code canSeeMission},
 * {@code canEditInventoryItem}, ...) will land here in Phase 3 once the corresponding services are
 * adapted - the basic {@link #canSeeSquadron(UUID)} / {@link #canEditSquadron(UUID)} primitives in
 * this class are enough to express them via {@code @PreAuthorize} SpEL.
 */
@Service
@RequiredArgsConstructor
public class SquadronScopeService {

  /**
   * Name of the HTTP request header through which the frontend relays the admin's active squadron
   * selection. A {@code null}/missing value means "no active selection" (admin sees all squadrons);
   * a non-blank UUID restricts the admin to that squadron's data for the duration of this request.
   * Source of truth lives on the frontend (Redis-backed Spring Session via {@code
   * MeFrontendController}); the backend treats the header as untrusted-but-bounded input — only
   * honoured for principals that already carry {@code ROLE_ADMIN}.
   */
  public static final String ACTIVE_SQUADRON_HEADER = "X-Active-Squadron-Id";

  /**
   * Request-attribute key under which the result of {@link #readPersistentSquadronFromUser()} is
   * cached for the duration of the current HTTP request. Stored as {@code Optional<UUID>} (never
   * {@code null}) so the cache can distinguish "resolved to empty" from "not yet resolved".
   */
  private static final String CACHE_KEY_PERSISTENT_USER_SQUADRON_ID =
      SquadronScopeService.class.getName() + ".persistentUserSquadronId";

  /**
   * Request-attribute key under which the result of {@link #currentSquadron()} is cached for the
   * duration of the current HTTP request. Same distinction-via-Optional contract as {@link
   * #CACHE_KEY_PERSISTENT_USER_SQUADRON_ID}.
   */
  private static final String CACHE_KEY_CURRENT_SQUADRON =
      SquadronScopeService.class.getName() + ".currentSquadron";

  private final AuthHelperService authHelper;
  private final UserRepository userRepository;
  private final SquadronRepository squadronRepository;
  private final MissionRepository missionRepository;
  private final InventoryItemRepository inventoryItemRepository;
  private final RefineryOrderRepository refineryOrderRepository;
  private final OperationRepository operationRepository;
  private final ShipRepository shipRepository;
  private final HttpServletRequest request;

  /**
   * Returns the squadron context that filters the current request. For admins this reads the {@code
   * X-Active-Squadron-Id} request header (the frontend's squadron switcher pushed there via {@code
   * ActiveSquadronRelayFilter}); for everyone else this loads the user's persistent home squadron.
   * Empty result means "no filter" for admins ("all squadrons") and "no access" for non-admins
   * (typically unauthenticated/anonymous).
   *
   * <p>The non-admin branch's {@code userRepository.findById} round-trip is memoised on the
   * HttpServletRequest via {@link #readPersistentSquadronFromUser()}, so repeated calls within the
   * same request collapse to a single DB hit. The admin branch reads the request header (in-memory)
   * and is not cached separately - it is already constant-time.
   */
  @NotNull
  public Optional<UUID> currentSquadronId() {
    if (authHelper.isAdmin()) {
      return readActiveSquadronFromHeader();
    }
    return readPersistentSquadronFromUser();
  }

  /**
   * Convenience entry point for the aggregate-service create paths: returns the {@link Squadron}
   * entity that matches {@link #currentSquadronId()}, loaded from the DB. Empty when the caller has
   * no effective squadron (admin in "all squadrons" mode, guest, or unauthenticated). Services use
   * this to stamp {@code owningSquadron} on newly-created aggregates that have no owner field of
   * their own (e.g. {@code Operation}) - aggregates that DO carry an owner ({@code Ship}, {@code
   * Mission}, ...) prefer to derive the squadron from the owner so a future user-squadron move does
   * not silently retag history.
   *
   * <p>Result is memoised per HttpServletRequest so repeated calls in one request (e.g. {@code
   * isPromotionFeatureEnabledForCurrentScope} after a separate {@code currentSquadron} hit on a
   * write path) collapse to a single {@code squadronRepository.findById} round-trip.
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
   * {@code true} iff the current principal may see data owned by {@code squadronId}.
   *
   * <ul>
   *   <li>Admin without an active squadron header: always {@code true}.
   *   <li>Admin with an active squadron header: {@code true} only for the selected squadron - the
   *       admin opted into the focused view and must switch back to "all" to break out.
   *   <li>Non-admin: {@code true} only for the user's home squadron.
   * </ul>
   *
   * @param squadronId the squadron whose data the caller wants to read; never {@code null}.
   */
  public boolean canSeeSquadron(@NotNull UUID squadronId) {
    if (authHelper.isAdmin()) {
      return readActiveSquadronFromHeader().map(active -> active.equals(squadronId)).orElse(true);
    }
    return readPersistentSquadronFromUser().map(active -> active.equals(squadronId)).orElse(false);
  }

  /**
   * {@code true} iff the current principal may write to data owned by {@code squadronId}. Identical
   * rule to {@link #canSeeSquadron(UUID)} - write access tracks read access for the squadron-scoped
   * aggregates. Kept as a separate method so future read/write divergence (e.g. a read-only viewer
   * role) can land here without breaking existing call sites.
   *
   * @param squadronId the squadron whose data the caller wants to write; never {@code null}.
   */
  public boolean canEditSquadron(@NotNull UUID squadronId) {
    return canSeeSquadron(squadronId);
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
   * owning-squadron check - {@link #canSeeMission(UUID)}'s public-mission escape clause does NOT
   * apply to write operations (MULTI_SQUADRON_PLAN.md section 1: editing/finalising is the owning
   * squadron's prerogative). Non-existent ids return {@code false}.
   *
   * @param missionId mission to inspect; never {@code null}.
   */
  public boolean canEditMission(@NotNull UUID missionId) {
    return missionRepository
        .findById(missionId)
        .map(m -> m.getOwningSquadron() == null || canEditSquadron(m.getOwningSquadron().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may read inventory item {@code itemId} directly (the
   * Lager-direct path - NOT the Job-Order-Kontext path, which is ungated by design). Strict
   * owning-squadron check. Non-existent ids return {@code false}.
   *
   * @param itemId inventory item to inspect; never {@code null}.
   */
  public boolean canSeeInventoryItem(@NotNull UUID itemId) {
    return inventoryItemRepository
        .findById(itemId)
        .map(i -> i.getOwningSquadron() == null || canSeeSquadron(i.getOwningSquadron().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may edit inventory item {@code itemId} directly. Strict
   * owning-squadron check - Job-Order-Kontext handover writes are gated separately by {@code
   * JobOrderHandoverService}'s {@code item.jobOrderId == currentOrder.id} guard. Non-existent ids
   * return {@code false}.
   *
   * @param itemId inventory item to inspect; never {@code null}.
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
   */
  public boolean canEditOperation(@NotNull UUID operationId) {
    return operationRepository
        .findById(operationId)
        .map(o -> o.getOwningSquadron() == null || canEditSquadron(o.getOwningSquadron().getId()))
        .orElse(false);
  }

  /**
   * {@code true} iff the current principal may read ship {@code shipId}. Strict owning-squadron
   * check (Hangar = strict eigene Staffel per MULTI_SQUADRON_PLAN.md section 1). Non-existent ids
   * return {@code false}.
   *
   * @param shipId ship to inspect; never {@code null}.
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
   * <p>Resolution rules:
   *
   * <ul>
   *   <li>Admin — always {@code true}; admins must retain access regardless of the flag so they can
   *       re-enable a squadron that locked itself out and pick up exactly where it left off.
   *   <li>Caller has an effective squadron — returns the flag stored on that squadron's row.
   *   <li>Caller has no effective squadron (unauthenticated / member without squadron) — {@code
   *       true}, since the squadron-scope filter already returns empty lists for them and there is
   *       no concrete squadron whose flag would apply.
   * </ul>
   *
   * @return {@code true} when the promotion menu may be exposed for the caller, {@code false} when
   *     a non-admin caller's home squadron has the feature disabled.
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
    String raw = request.getHeader(ACTIVE_SQUADRON_HEADER);
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(raw.trim()));
    } catch (IllegalArgumentException ex) {
      // Malformed header → treat as "no selection". Logging at debug so a stray client cannot
      // spam the WARN channel; the effective behaviour ("admin sees all") matches what the
      // admin gets when they have not chosen a squadron yet.
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
   * Reads a previously-cached {@link Optional} from the current HttpServletRequest under {@code
   * key}. The outer {@link Optional} of the return value signals presence in the cache: an outer
   * {@link Optional#empty()} means "key not yet written, do the real work", while a present outer
   * Optional wraps the cached value (which may itself be {@link Optional#empty()} for the
   * "resolved-to-empty" case). Keeps the unchecked cast confined to a single helper instead of
   * being repeated at every call site.
   *
   * @param key request-attribute key under which the cached Optional was previously stored
   * @param <T> element type of the cached Optional
   * @return outer-present iff the key has been written this request; the inner Optional is the
   *     cached value as written
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
