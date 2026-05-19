package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.mapper.RankRequirementMapper;
import de.greluc.krt.iri.basetool.backend.model.PromotionCategory;
import de.greluc.krt.iri.basetool.backend.model.PromotionTopic;
import de.greluc.krt.iri.basetool.backend.model.RankRequirement;
import de.greluc.krt.iri.basetool.backend.model.dto.RankRequirementCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.RankRequirementResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.RankRequirementUpdateRequest;
import de.greluc.krt.iri.basetool.backend.repository.PromotionCategoryRepository;
import de.greluc.krt.iri.basetool.backend.repository.PromotionTopicRepository;
import de.greluc.krt.iri.basetool.backend.repository.RankRequirementRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Domain service for {@link RankRequirement} CRUD operations. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RankRequirementService {

  public static final Set<String> SORTABLE_FIELDS =
      Set.of("id", "fromRank", "toRank", "minimumLevel", "requiredCount", "createdAt", "updatedAt");
  public static final String DEFAULT_SORT_FIELD = "fromRank";

  private final RankRequirementRepository repository;
  private final PromotionTopicRepository topicRepository;
  private final PromotionCategoryRepository categoryRepository;
  private final RankRequirementMapper mapper;
  private final SquadronScopeService squadronScopeService;

  /**
   * Returns a paginated slice of every {@link RankRequirementResponse} across all rank transitions.
   * The controller validates the caller-supplied sort against {@link #SORTABLE_FIELDS} before this
   * method is invoked.
   *
   * @param pageable Spring Data paging and sorting parameters
   * @return a page of rank requirements
   */
  public Page<RankRequirementResponse> list(@NotNull Pageable pageable) {
    if (!squadronScopeService.isPromotionFeatureEnabledForCurrentScope()) {
      return Page.empty(pageable);
    }
    return repository.findAll(pageable).map(mapper::toResponse);
  }

  /**
   * Returns every {@link RankRequirementResponse} that applies to the promotion path from {@code
   * fromRank} to {@code toRank}, used by the eligibility engine to evaluate whether a member
   * qualifies for that rank step.
   *
   * @param fromRank ordinal of the current rank
   * @param toRank ordinal of the rank being promoted to
   * @return the rank requirements applicable to that transition
   */
  public List<RankRequirementResponse> listByRanks(int fromRank, int toRank) {
    if (!squadronScopeService.isPromotionFeatureEnabledForCurrentScope()) {
      return List.of();
    }
    return repository.findAllByFromRankAndToRankOrderByIdAsc(fromRank, toRank).stream()
        .map(mapper::toResponse)
        .toList();
  }

  /**
   * Resolves a single {@link RankRequirementResponse} by identifier.
   *
   * @param id identifier of the rank requirement
   * @return the matching rank requirement in response form
   * @throws EntityNotFoundException if no rank requirement exists for that id
   */
  public RankRequirementResponse get(@NotNull UUID id) {
    squadronScopeService.assertPromotionFeatureEnabled();
    return mapper.toResponse(load(id));
  }

  /**
   * Persists a new {@link RankRequirement}, wiring optional topic and category associations from
   * the request. Restricted to ADMIN or OFFICER callers via {@link PreAuthorize}.
   *
   * @param request validated payload describing the new rank requirement
   * @return the persisted rank requirement in response form
   * @throws EntityNotFoundException if a referenced topic or category does not exist
   */
  @Transactional
  @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
  public RankRequirementResponse create(@NotNull RankRequirementCreateRequest request) {
    squadronScopeService.assertPromotionFeatureEnabled();
    validateSingleRankStep(request.fromRank(), request.toRank());
    RankRequirement entity = mapper.toEntity(request);
    PromotionTopic resolvedTopic = resolveTopic(request.topicId());
    PromotionCategory resolvedCategory = resolveCategory(request.categoryId());
    assertCallerMayEditScope(resolvedTopic, resolvedCategory);
    entity.setTopic(resolvedTopic);
    entity.setCategory(resolvedCategory);
    RankRequirement saved = repository.save(entity);
    log.info(
        "Created RankRequirement id={} {}->{}",
        saved.getId(),
        saved.getFromRank(),
        saved.getToRank());
    return mapper.toResponse(saved);
  }

  /**
   * Updates the rank requirement identified by {@code id} and re-resolves its optional topic and
   * category references. The caller-supplied {@code version} is compared against the loaded entity
   * and a mismatch produces an {@link ObjectOptimisticLockingFailureException} that surfaces as
   * HTTP 409.
   *
   * @param id identifier of the rank requirement to update
   * @param request validated payload with the new field values and the previously fetched {@code
   *     version}
   * @return the updated rank requirement in response form
   * @throws EntityNotFoundException if the requirement or any referenced topic/category does not
   *     exist
   * @throws ObjectOptimisticLockingFailureException if the request's {@code version} no longer
   *     matches the persisted entity
   */
  @Transactional
  @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
  public RankRequirementResponse update(
      @NotNull UUID id, @NotNull RankRequirementUpdateRequest request) {
    squadronScopeService.assertPromotionFeatureEnabled();
    validateSingleRankStep(request.fromRank(), request.toRank());
    RankRequirement entity = load(id);
    assertCallerMayEditScope(entity.getTopic(), entity.getCategory());
    if (!entity.getVersion().equals(request.version())) {
      throw new ObjectOptimisticLockingFailureException(RankRequirement.class, id);
    }
    mapper.updateEntity(entity, request);
    PromotionTopic resolvedTopic = resolveTopic(request.topicId());
    PromotionCategory resolvedCategory = resolveCategory(request.categoryId());
    assertCallerMayEditScope(resolvedTopic, resolvedCategory);
    entity.setTopic(resolvedTopic);
    entity.setCategory(resolvedCategory);
    RankRequirement saved = repository.save(entity);
    log.info("Updated RankRequirement id={}", id);
    return mapper.toResponse(saved);
  }

  /**
   * Permanently removes the rank requirement identified by {@code id}. Restricted to ADMIN or
   * OFFICER callers.
   *
   * @param id identifier of the rank requirement to delete
   * @throws EntityNotFoundException if no rank requirement exists for that id
   */
  @Transactional
  @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
  public void delete(@NotNull UUID id) {
    squadronScopeService.assertPromotionFeatureEnabled();
    RankRequirement entity = load(id);
    assertCallerMayEditScope(entity.getTopic(), entity.getCategory());
    repository.delete(entity);
    log.info("Deleted RankRequirement id={}", id);
  }

  private void assertCallerMayEditScope(
      @Nullable PromotionTopic topic, @Nullable PromotionCategory category) {
    PromotionTopic effectiveTopic =
        topic != null ? topic : category != null ? category.getTopic() : null;
    if (effectiveTopic == null || effectiveTopic.getOwningSquadron() == null) {
      return;
    }
    if (!squadronScopeService.canEditSquadron(effectiveTopic.getOwningSquadron().getId())) {
      throw new AccessDeniedException(
          "Caller's squadron context does not allow editing rank requirements of this scope");
    }
  }

  /**
   * Enforces that a rank requirement always describes a single-step promotion ({@code fromRank -
   * toRank == 1}), e.g. {@code 20 -> 19}. Multi-step transitions like {@code 20 -> 18} would be
   * structurally ambiguous for the eligibility evaluator (which rules apply, the source or the
   * target rank's?) and are therefore rejected at the service boundary.
   *
   * <p>Lower {@code rank} ordinals denote higher ranks in this codebase, so a valid promotion
   * always decreases the ordinal by exactly one.
   *
   * @param fromRank ordinal of the current rank
   * @param toRank ordinal of the rank being promoted to
   * @throws BadRequestException if the difference is anything other than {@code 1}
   */
  private static void validateSingleRankStep(int fromRank, int toRank) {
    if (fromRank - toRank != 1) {
      throw new BadRequestException("error.rank_requirement.invalid_step");
    }
  }

  @NotNull
  private RankRequirement load(@NotNull UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("RankRequirement not found: " + id));
  }

  @Nullable
  private PromotionTopic resolveTopic(@Nullable UUID topicId) {
    if (topicId == null) {
      return null;
    }
    return topicRepository
        .findById(topicId)
        .orElseThrow(() -> new EntityNotFoundException("PromotionTopic not found: " + topicId));
  }

  @Nullable
  private PromotionCategory resolveCategory(@Nullable UUID categoryId) {
    if (categoryId == null) {
      return null;
    }
    return categoryRepository
        .findById(categoryId)
        .orElseThrow(
            () -> new EntityNotFoundException("PromotionCategory not found: " + categoryId));
  }
}
