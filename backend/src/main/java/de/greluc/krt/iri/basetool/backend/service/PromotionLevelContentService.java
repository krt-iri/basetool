package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.PromotionLevelContentMapper;
import de.greluc.krt.iri.basetool.backend.model.PromotionCategory;
import de.greluc.krt.iri.basetool.backend.model.PromotionLevelContent;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionLevelContentCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionLevelContentResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionLevelContentUpdateRequest;
import de.greluc.krt.iri.basetool.backend.repository.PromotionCategoryRepository;
import de.greluc.krt.iri.basetool.backend.repository.PromotionLevelContentRepository;
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

/** Domain service for {@link PromotionLevelContent} CRUD operations. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PromotionLevelContentService {

  public static final Set<String> SORTABLE_FIELDS = Set.of("id", "level", "createdAt", "updatedAt");
  public static final String DEFAULT_SORT_FIELD = "level";

  private final PromotionLevelContentRepository repository;
  private final PromotionCategoryRepository categoryRepository;
  private final PromotionLevelContentMapper mapper;
  private final OwnerScopeService ownerScopeService;

  /**
   * Returns a paginated slice of every {@link PromotionLevelContentResponse} across all categories.
   * The controller validates the caller-supplied sort against {@link #SORTABLE_FIELDS} before this
   * method is invoked.
   *
   * @param pageable Spring Data paging and sorting parameters
   * @return a page of level contents
   */
  public Page<PromotionLevelContentResponse> list(@NotNull Pageable pageable) {
    if (!ownerScopeService.isPromotionFeatureEnabledForCurrentScope()) {
      return Page.empty(pageable);
    }
    return repository.findAll(pageable).map(mapper::toResponse);
  }

  /**
   * Returns every {@link PromotionLevelContentResponse} for the given category ordered by {@link
   * de.greluc.krt.iri.basetool.backend.model.PromotionLevel}, used by the promotion UI to render
   * the rank-progression table without pagination.
   *
   * @param categoryId identifier of the parent category
   * @return the category's level contents in display order
   */
  public List<PromotionLevelContentResponse> listByCategory(@NotNull UUID categoryId) {
    if (!ownerScopeService.isPromotionFeatureEnabledForCurrentScope()) {
      return List.of();
    }
    return repository.findAllByCategoryIdOrderByLevel(categoryId).stream()
        .map(mapper::toResponse)
        .toList();
  }

  /**
   * Resolves a single {@link PromotionLevelContentResponse} by identifier.
   *
   * @param id identifier of the level content
   * @return the matching level content in response form
   * @throws EntityNotFoundException if no level content exists for that id
   */
  public PromotionLevelContentResponse get(@NotNull UUID id) {
    ownerScopeService.assertPromotionFeatureEnabled();
    return mapper.toResponse(load(id));
  }

  /**
   * Persists a new {@link PromotionLevelContent} attached to the category referenced by the
   * request. Restricted to ADMIN or OFFICER callers via {@link PreAuthorize}.
   *
   * @param request validated payload describing the new level content
   * @return the persisted level content in response form
   * @throws EntityNotFoundException if the referenced category does not exist
   */
  @Transactional
  @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
  public PromotionLevelContentResponse create(@NotNull PromotionLevelContentCreateRequest request) {
    ownerScopeService.assertPromotionFeatureEnabled();
    PromotionCategory category =
        categoryRepository
            .findById(request.categoryId())
            .orElseThrow(
                () ->
                    new EntityNotFoundException(
                        "PromotionCategory not found: " + request.categoryId()));
    assertCallerMayEditCategory(category);
    PromotionLevelContent entity = mapper.toEntity(request);
    entity.setCategory(category);
    PromotionLevelContent saved = repository.save(entity);
    log.info("Created PromotionLevelContent id={} level={}", saved.getId(), saved.getLevel());
    return mapper.toResponse(saved);
  }

  /**
   * Updates the level content identified by {@code id} and rebinds it to the category referenced by
   * the request. The caller-supplied {@code version} is compared against the loaded entity and a
   * mismatch produces an {@link ObjectOptimisticLockingFailureException} that surfaces as HTTP 409.
   *
   * @param id identifier of the level content to update
   * @param request validated payload with the new field values and the previously fetched {@code
   *     version}
   * @return the updated level content in response form
   * @throws EntityNotFoundException if the level content or referenced category does not exist
   * @throws ObjectOptimisticLockingFailureException if the request's {@code version} no longer
   *     matches the persisted entity
   */
  @Transactional
  @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
  public PromotionLevelContentResponse update(
      @NotNull UUID id, @NotNull PromotionLevelContentUpdateRequest request) {
    ownerScopeService.assertPromotionFeatureEnabled();
    PromotionLevelContent entity = load(id);
    assertCallerMayEditCategory(entity.getCategory());
    if (!entity.getVersion().equals(request.version())) {
      throw new ObjectOptimisticLockingFailureException(PromotionLevelContent.class, id);
    }
    PromotionCategory category =
        categoryRepository
            .findById(request.categoryId())
            .orElseThrow(
                () ->
                    new EntityNotFoundException(
                        "PromotionCategory not found: " + request.categoryId()));
    assertCallerMayEditCategory(category);
    mapper.updateEntity(entity, request);
    entity.setCategory(category);
    PromotionLevelContent saved = repository.save(entity);
    log.info("Updated PromotionLevelContent id={}", id);
    return mapper.toResponse(saved);
  }

  /**
   * Permanently removes the level content identified by {@code id}. Restricted to ADMIN or OFFICER
   * callers.
   *
   * @param id identifier of the level content to delete
   * @throws EntityNotFoundException if no level content exists for that id
   */
  @Transactional
  @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
  public void delete(@NotNull UUID id) {
    ownerScopeService.assertPromotionFeatureEnabled();
    PromotionLevelContent entity = load(id);
    assertCallerMayEditCategory(entity.getCategory());
    repository.delete(entity);
    log.info("Deleted PromotionLevelContent id={}", id);
  }

  @NotNull
  private PromotionLevelContent load(@NotNull UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("PromotionLevelContent not found: " + id));
  }

  private void assertCallerMayEditCategory(PromotionCategory category) {
    if (category == null
        || category.getTopic() == null
        || category.getTopic().getOwningSquadron() == null) {
      return;
    }
    if (!ownerScopeService.canEditSquadron(category.getTopic().getOwningSquadron().getId())) {
      throw new AccessDeniedException(
          "Caller's squadron context does not allow editing level contents of this scope");
    }
  }
}
