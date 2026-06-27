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

import de.greluc.krt.profit.basetool.backend.mapper.MemberEvaluationMapper;
import de.greluc.krt.profit.basetool.backend.model.MemberEvaluation;
import de.greluc.krt.profit.basetool.backend.model.PromotionCategory;
import de.greluc.krt.profit.basetool.backend.model.dto.MemberEvaluationResponse;
import de.greluc.krt.profit.basetool.backend.model.dto.MemberEvaluationUpdateRequest;
import de.greluc.krt.profit.basetool.backend.repository.MemberEvaluationRepository;
import de.greluc.krt.profit.basetool.backend.repository.PromotionCategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service for {@link MemberEvaluation}.
 *
 * <p>Data isolation: read operations for personal views are filtered by {@code userId} (JWT sub).
 * Write operations (assign/update level) are restricted to ADMIN and OFFICER callers, with two
 * squadron-scope guards that BOTH must pass for a non-admin: an Officer of squadron X may only
 * manage evaluations whose category belongs to a topic owned by squadron X ({@link
 * #assertCallerMayEditCategory}) AND whose evaluated member belongs to squadron X ({@link
 * #assertCallerMayEvaluateUser} — the target-member scope check added by the gap-fill security
 * audit; without it an officer could write a foreign-squadron member's evaluation row). Admins span
 * every squadron unless they have focused the sidebar switcher.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MemberEvaluationService {

  public static final Set<String> SORTABLE_FIELDS =
      Set.of("id", "userId", "assignedLevel", "createdAt", "updatedAt");
  public static final String DEFAULT_SORT_FIELD = "updatedAt";

  private final MemberEvaluationRepository repository;
  private final PromotionCategoryRepository categoryRepository;
  private final MemberEvaluationMapper mapper;
  private final OwnerScopeService ownerScopeService;
  private final OrgUnitMembershipService orgUnitMembershipService;
  private final AuthHelperService authHelperService;

  /**
   * Returns all evaluations for the given user (JWT-sub filtered – data isolation), additionally
   * scoped to the caller's active squadron so a multi-squadron member's "my evaluations" only shows
   * the active squadron's grades.
   */
  public List<MemberEvaluationResponse> listForUser(@NotNull String userId) {
    if (!ownerScopeService.isPromotionFeatureEnabledForCurrentScope()
        || !ownerScopeService.hasPromotionReadAccess()) {
      return List.of();
    }
    UUID scope = ownerScopeService.currentSquadronId().orElse(null);
    return repository.findAllByUserIdScoped(userId, scope).stream()
        .map(mapper::toResponse)
        .toList();
  }

  /** Returns paginated evaluations for the given user, scoped to the active squadron. */
  public Page<MemberEvaluationResponse> listForUserPaged(
      @NotNull String userId, @NotNull Pageable pageable) {
    if (!ownerScopeService.isPromotionFeatureEnabledForCurrentScope()
        || !ownerScopeService.hasPromotionReadAccess()) {
      return Page.empty(pageable);
    }
    UUID scope = ownerScopeService.currentSquadronId().orElse(null);
    return repository.findAllByUserIdScoped(userId, scope, pageable).map(mapper::toResponse);
  }

  /** Returns all evaluations (admin view, all users) scoped to the active squadron. */
  @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
  public Page<MemberEvaluationResponse> listAll(@NotNull Pageable pageable) {
    if (!ownerScopeService.isPromotionFeatureEnabledForCurrentScope()
        || !ownerScopeService.hasPromotionReadAccess()) {
      return Page.empty(pageable);
    }
    UUID scope = ownerScopeService.currentSquadronId().orElse(null);
    return repository.findAllScoped(scope, pageable).map(mapper::toResponse);
  }

  /**
   * Upserts (create or update) an evaluation for a user/category combination. ADMIN or OFFICER of
   * the category's owning squadron.
   */
  @Transactional
  @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
  public MemberEvaluationResponse upsert(
      @NotNull String userId,
      @NotNull UUID categoryId,
      @NotNull MemberEvaluationUpdateRequest request) {
    ownerScopeService.assertPromotionFeatureEnabled();
    PromotionCategory category =
        categoryRepository
            .findById(categoryId)
            .orElseThrow(
                () -> new EntityNotFoundException("PromotionCategory not found: " + categoryId));
    assertCallerMayEditCategory(category);
    assertCallerMayEvaluateUser(userId);

    MemberEvaluation entity =
        repository
            .findByUserIdAndCategoryId(userId, categoryId)
            .orElseGet(() -> MemberEvaluation.builder().userId(userId).category(category).build());

    if (entity.getId() != null && !entity.getVersion().equals(request.version())) {
      throw new ObjectOptimisticLockingFailureException(MemberEvaluation.class, entity.getId());
    }

    entity.setAssignedLevel(request.assignedLevel());
    MemberEvaluation saved = repository.save(entity);
    log.info(
        "Upserted MemberEvaluation userId={} categoryId={} level={}",
        userId,
        categoryId,
        request.assignedLevel());
    return mapper.toResponse(saved);
  }

  /** Deletes an evaluation entry (removes the assigned level). */
  @Transactional
  @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
  public void delete(@NotNull UUID id) {
    ownerScopeService.assertPromotionFeatureEnabled();
    MemberEvaluation entity =
        repository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("MemberEvaluation not found: " + id));
    assertCallerMayEditCategory(entity.getCategory());
    assertCallerMayEvaluateUser(entity.getUserId());
    repository.delete(entity);
    log.info("Deleted MemberEvaluation id={}", id);
  }

  private void assertCallerMayEditCategory(PromotionCategory category) {
    if (category == null
        || category.getTopic() == null
        || category.getTopic().getOwningSquadron() == null) {
      return;
    }
    if (!ownerScopeService.canEditSquadron(category.getTopic().getOwningSquadron().getId())) {
      throw new AccessDeniedException(
          "Caller's squadron context does not allow editing evaluations of this scope");
    }
  }

  /**
   * Security audit (gap-fill): asserts the caller may evaluate the <em>member being evaluated</em>,
   * not just the category. {@link #assertCallerMayEditCategory} validates only the category's
   * owning squadron, so without this an OFFICER of squadron X could create/overwrite/delete an
   * evaluation row for a member of squadron Y as long as the category belongs to X (a cross-tenant
   * write of member-evaluation data). Admins span every squadron and short-circuit; otherwise at
   * least one of the target member's Staffeln must be within the caller's editable scope ({@link
   * OwnerScopeService#canEditSquadron(UUID)}), mirroring the officer-scope rule the rest of the
   * promotion area enforces. Fails closed when the user id is malformed or the member has no
   * Staffel the caller can edit.
   *
   * <p>REQ-ORG-017: the target may now hold up to two Staffeln, so the gate ORs across ALL of them
   * — the caller may evaluate the member as soon as it can edit ANY one of the member's Staffeln,
   * so a shared second Staffel is honoured rather than silently dropped.
   *
   * @param userId the Keycloak sub (== app_user id) of the member being evaluated; never {@code
   *     null}.
   */
  private void assertCallerMayEvaluateUser(@NotNull String userId) {
    if (authHelperService.isAdmin()) {
      return;
    }
    UUID targetUserId;
    try {
      targetUserId = UUID.fromString(userId);
    } catch (IllegalArgumentException e) {
      throw new AccessDeniedException("Evaluated member id is not a valid identifier");
    }
    java.util.List<UUID> staffelIds =
        orgUnitMembershipService.findStaffelMembershipOrgUnitIds(targetUserId);
    if (staffelIds.stream().noneMatch(ownerScopeService::canEditSquadron)) {
      throw new AccessDeniedException(
          "Caller's squadron context does not allow evaluating this member");
    }
  }
}
