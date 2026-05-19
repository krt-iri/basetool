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
  private final SquadronScopeService squadronScopeService;

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
  @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
  public Page<MemberEvaluationResponse> listAll(@NotNull Pageable pageable) {
    return repository.findAll(pageable).map(mapper::toResponse);
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
    if (!squadronScopeService.canEditSquadron(category.getTopic().getOwningSquadron().getId())) {
      throw new AccessDeniedException(
          "Caller's squadron context does not allow editing evaluations of this scope");
    }
  }
}
