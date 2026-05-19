package de.greluc.krt.iri.basetool.backend.service;

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
   * <p>The call hits the DB for non-admin requests (one {@code userRepository.findById} per call).
   * Callers in hot paths should resolve it once per request rather than per filtered row.
   */
  @NotNull
  public Optional<UUID> currentSquadronId() {
    if (authHelper.isAdmin()) {
      return readActiveSquadronFromHeader();
    }
    return authHelper
        .currentUserId()
        .flatMap(userRepository::findById)
        .map(User::getSquadron)
        .map(Squadron::getId);
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
   * @return the {@link Squadron} for the current effective context, or empty when none applies.
   */
  @NotNull
  public Optional<Squadron> currentSquadron() {
    return currentSquadronId().flatMap(squadronRepository::findById);
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
   * @param missionId mission to inspect; never {@code null}.
   */
  public boolean canSeeMission(@NotNull UUID missionId) {
    return missionRepository
        .findById(missionId)
        .map(
            m -> {
              if (m.getOwningSquadron() == null) {
                return true;
              }
              if (canSeeSquadron(m.getOwningSquadron().getId())) {
                return true;
              }
              return !Boolean.TRUE.equals(m.getIsInternal());
            })
        .orElse(false);
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
    return authHelper
        .currentUserId()
        .flatMap(userRepository::findById)
        .map(User::getSquadron)
        .map(Squadron::getId);
  }
}
