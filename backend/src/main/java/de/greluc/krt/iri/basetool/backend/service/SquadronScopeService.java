package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Squadron;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compatibility shim that preserves the {@code squadronScopeService} bean name and class identity
 * during the R2.c rollout. Every method delegates to {@link OwnerScopeService} — see that class
 * for the actual implementation, the caching contract, and the org-unit-context resolution
 * semantics.
 *
 * <p>Why the shim exists: the project's {@code @PreAuthorize} expressions reach into this bean by
 * name (currently ~30 SpEL strings like {@code @squadronScopeService.canEditMission(#id)}) and the
 * ArchUnit rule {@code staffelScopedServicesMustWireSquadronOrAuthHelper} verifies that every
 * staffel-scoped service injects {@code SquadronScopeService}. Replacing the class in one step
 * would force a synchronized rewrite of every SpEL string, every test mock, and every ArchUnit
 * whitelist entry — too large a diff to review safely. The shim keeps every existing
 * SpEL/inject/test path working through R2.c so the next PR (R2.d) can migrate the SpEL strings
 * onto the {@code @ownerScopeService.*} surface at its own pace before this shim is finally
 * deleted.
 *
 * <p>Caching note: this class holds no cache state of its own. The per-request caches that
 * memoise the squadron-context resolution live on {@link OwnerScopeService} and survive the
 * delegation because both beans share the same request scope. The {@code @Transactional(readOnly =
 * true)} class-level annotation is preserved here purely for ArchUnit ergonomics (the read-only
 * marker mirrors the original {@code SquadronScopeService}); the actual transactional boundary
 * already exists on {@link OwnerScopeService} so the shim's marker is redundant but harmless.
 *
 * <p>Removal plan: R2.d migrates the {@code @PreAuthorize} SpEL strings and the ArchUnit
 * whitelist entry to {@code @ownerScopeService} / {@code OwnerScopeService.class}, after which the
 * Spring auto-wiring in {@link AuthHelperService#scope()} and the test {@code @Mock} declarations
 * become the only remaining consumers — those move at the same time and this class file is then
 * deleted in the same PR.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SquadronScopeService {

  /**
   * Re-export of {@link OwnerScopeService#ACTIVE_SQUADRON_HEADER} so existing static references
   * keep compiling. The value is the same instance — Java {@code static final String}s are interned
   * and the field is initialised at class-load time, so no double-bookkeeping is possible.
   */
  public static final String ACTIVE_SQUADRON_HEADER = OwnerScopeService.ACTIVE_SQUADRON_HEADER;

  private final OwnerScopeService delegate;

  /**
   * Delegates to {@link OwnerScopeService#currentSquadronId()}. See the delegate for the exact
   * resolution rule (admin reads the request header, non-admins resolve to their persistent home
   * squadron).
   *
   * @return the active squadron id, or empty when no filter applies.
   */
  @NotNull
  public Optional<UUID> currentSquadronId() {
    return delegate.currentSquadronId();
  }

  /**
   * Delegates to {@link OwnerScopeService#currentSquadron()}. See the delegate for caching and the
   * usage contract (services stamp {@code owningSquadron} on newly-created aggregates that have no
   * owner field of their own).
   *
   * @return the {@link Squadron} for the current effective context, or empty when none applies.
   */
  @NotNull
  public Optional<Squadron> currentSquadron() {
    return delegate.currentSquadron();
  }

  /**
   * Delegates to {@link OwnerScopeService#canSeeSquadron(UUID)}. See the delegate for the exact
   * admin / non-admin / active-header rule.
   *
   * @param squadronId the squadron whose data the caller wants to read; never {@code null}.
   * @return {@code true} iff the caller may see the given squadron's data.
   */
  public boolean canSeeSquadron(@NotNull UUID squadronId) {
    return delegate.canSeeSquadron(squadronId);
  }

  /**
   * Delegates to {@link OwnerScopeService#canEditSquadron(UUID)}. Identical rule to
   * {@link #canSeeSquadron(UUID)} — kept as a separate method so future read/write divergence does
   * not need a re-shape of the SpEL surface.
   *
   * @param squadronId the squadron whose data the caller wants to write; never {@code null}.
   * @return {@code true} iff the caller may write to the given squadron's data.
   */
  public boolean canEditSquadron(@NotNull UUID squadronId) {
    return delegate.canEditSquadron(squadronId);
  }

  /**
   * Delegates to {@link OwnerScopeService#canSeeMission(UUID)}. See the delegate for the
   * cross-staffel public-mission visibility rule plus the M-2 / M-3 audit hardenings.
   *
   * @param missionId mission to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the mission.
   */
  public boolean canSeeMission(@NotNull UUID missionId) {
    return delegate.canSeeMission(missionId);
  }

  /**
   * Delegates to {@link OwnerScopeService#canEditMission(UUID)} — strict owning-squadron check,
   * no public-mission escape on writes.
   *
   * @param missionId mission to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the mission.
   */
  public boolean canEditMission(@NotNull UUID missionId) {
    return delegate.canEditMission(missionId);
  }

  /**
   * Delegates to {@link OwnerScopeService#canSeeInventoryItem(UUID)}.
   *
   * @param itemId inventory item to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the item.
   */
  public boolean canSeeInventoryItem(@NotNull UUID itemId) {
    return delegate.canSeeInventoryItem(itemId);
  }

  /**
   * Delegates to {@link OwnerScopeService#canEditInventoryItem(UUID)}.
   *
   * @param itemId inventory item to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the item.
   */
  public boolean canEditInventoryItem(@NotNull UUID itemId) {
    return delegate.canEditInventoryItem(itemId);
  }

  /**
   * Delegates to {@link OwnerScopeService#canSeeRefineryOrder(UUID)}.
   *
   * @param orderId refinery order to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the order.
   */
  public boolean canSeeRefineryOrder(@NotNull UUID orderId) {
    return delegate.canSeeRefineryOrder(orderId);
  }

  /**
   * Delegates to {@link OwnerScopeService#canEditRefineryOrder(UUID)}.
   *
   * @param orderId refinery order to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the order.
   */
  public boolean canEditRefineryOrder(@NotNull UUID orderId) {
    return delegate.canEditRefineryOrder(orderId);
  }

  /**
   * Delegates to {@link OwnerScopeService#canSeeOperation(UUID)}.
   *
   * @param operationId operation to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the operation.
   */
  public boolean canSeeOperation(@NotNull UUID operationId) {
    return delegate.canSeeOperation(operationId);
  }

  /**
   * Delegates to {@link OwnerScopeService#canEditOperation(UUID)}.
   *
   * @param operationId operation to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the operation.
   */
  public boolean canEditOperation(@NotNull UUID operationId) {
    return delegate.canEditOperation(operationId);
  }

  /**
   * Delegates to {@link OwnerScopeService#canSeeShip(UUID)}.
   *
   * @param shipId ship to inspect; never {@code null}.
   * @return {@code true} iff the caller may read the ship.
   */
  public boolean canSeeShip(@NotNull UUID shipId) {
    return delegate.canSeeShip(shipId);
  }

  /**
   * Delegates to {@link OwnerScopeService#canEditShip(UUID)}.
   *
   * @param shipId ship to inspect; never {@code null}.
   * @return {@code true} iff the caller may edit the ship.
   */
  public boolean canEditShip(@NotNull UUID shipId) {
    return delegate.canEditShip(shipId);
  }

  /**
   * Delegates to {@link OwnerScopeService#isPromotionFeatureEnabledForCurrentScope()}.
   *
   * @return {@code true} when the promotion menu may be exposed for the caller.
   */
  public boolean isPromotionFeatureEnabledForCurrentScope() {
    return delegate.isPromotionFeatureEnabledForCurrentScope();
  }

  /**
   * Delegates to {@link OwnerScopeService#assertPromotionFeatureEnabled()}.
   *
   * @throws AccessDeniedException if a non-admin caller's home squadron has the flag disabled.
   */
  public void assertPromotionFeatureEnabled() {
    delegate.assertPromotionFeatureEnabled();
  }
}
