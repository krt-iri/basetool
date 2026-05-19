package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.MemberEvaluationMapper;
import de.greluc.krt.iri.basetool.backend.model.MemberEvaluation;
import de.greluc.krt.iri.basetool.backend.model.PromotionCategory;
import de.greluc.krt.iri.basetool.backend.model.dto.MemberEvaluationResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.MemberEvaluationUpdateRequest;
import de.greluc.krt.iri.basetool.backend.repository.MemberEvaluationRepository;
import de.greluc.krt.iri.basetool.backend.repository.PromotionCategoryRepository;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service for {@link MemberEvaluation}.
 *
 * <p>Data isolation: read operations for personal views are filtered by {@code userId} (JWT sub).
 * Write operations (assign/update level) are restricted to the ADMIN role. The promotion system
 * counts as system-wide configuration under the Phase-4 admin lockdown (MULTI_SQUADRON_PLAN.md
 * section 2: "Promotion-System-Pflege" sits in the admin bucket Officer no longer reaches).
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

  /** Returns all evaluations for the given user (JWT-sub filtered – data isolation). */
  public List<MemberEvaluationResponse> listForUser(@NotNull String userId) {
    return repository.findAllByUserId(userId).stream().map(mapper::toResponse).toList();
  }

  /** Returns paginated evaluations for the given user. */
  public Page<MemberEvaluationResponse> listForUserPaged(
      @NotNull String userId, @NotNull Pageable pageable) {
    return repository.findAllByUserId(userId, pageable).map(mapper::toResponse);
  }

  /** Returns all evaluations (admin view, all users). */
  @PreAuthorize("hasRole('ADMIN')")
  public Page<MemberEvaluationResponse> listAll(@NotNull Pageable pageable) {
    return repository.findAll(pageable).map(mapper::toResponse);
  }

  /**
   * Upserts (create or update) an evaluation for a user/category combination. Restricted to ADMIN.
   */
  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public MemberEvaluationResponse upsert(
      @NotNull String userId,
      @NotNull UUID categoryId,
      @NotNull MemberEvaluationUpdateRequest request) {
    PromotionCategory category =
        categoryRepository
            .findById(categoryId)
            .orElseThrow(
                () -> new EntityNotFoundException("PromotionCategory not found: " + categoryId));

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
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@NotNull UUID id) {
    MemberEvaluation entity =
        repository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("MemberEvaluation not found: " + id));
    repository.delete(entity);
    log.info("Deleted MemberEvaluation id={}", id);
  }
}
