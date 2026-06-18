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
 * Write operations (assign/update level) are restricted to ADMIN and OFFICER callers, with an
 * additional squadron-scope guard: an Officer of squadron X may only manage evaluations whose
 * category belongs to a topic owned by squadron X. Admins span every squadron unless they have
 * focused the sidebar switcher.
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
}
