package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.PromotionTopicMapper;
import de.greluc.krt.iri.basetool.backend.model.PromotionTopic;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionTopicCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionTopicResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionTopicUpdateRequest;
import de.greluc.krt.iri.basetool.backend.repository.PromotionTopicRepository;
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
 * Domain service for {@link PromotionTopic} CRUD operations. Write operations are restricted to
 * ADMIN and OFFICER roles.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PromotionTopicService {

  public static final Set<String> SORTABLE_FIELDS =
      Set.of("id", "name", "sortOrder", "createdAt", "updatedAt");
  public static final String DEFAULT_SORT_FIELD = "sortOrder";

  private final PromotionTopicRepository repository;
  private final PromotionTopicMapper mapper;

  /**
   * Returns a paginated slice of every {@link PromotionTopicResponse}. The controller validates the
   * caller-supplied sort against {@link #SORTABLE_FIELDS} before this method is invoked.
   *
   * @param pageable Spring Data paging and sorting parameters
   * @return a page of promotion topics
   */
  public Page<PromotionTopicResponse> list(@NotNull Pageable pageable) {
    return repository.findAll(pageable).map(mapper::toResponse);
  }

  /**
   * Returns every {@link PromotionTopicResponse} ordered by {@code sortOrder}, used by the
   * promotion UI to render the full topic list without pagination.
   *
   * @return every topic in display order
   */
  public List<PromotionTopicResponse> listAll() {
    return repository.findAllByOrderBySortOrderAsc().stream().map(mapper::toResponse).toList();
  }

  /**
   * Resolves a single {@link PromotionTopicResponse} by identifier.
   *
   * @param id identifier of the topic
   * @return the matching topic in response form
   * @throws EntityNotFoundException if no topic exists for that id
   */
  public PromotionTopicResponse get(@NotNull UUID id) {
    return mapper.toResponse(load(id));
  }

  /**
   * Persists a new {@link PromotionTopic}. Restricted to ADMIN or OFFICER callers via {@link
   * PreAuthorize}.
   *
   * @param request validated payload describing the new topic
   * @return the persisted topic in response form
   */
  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public PromotionTopicResponse create(@NotNull PromotionTopicCreateRequest request) {
    PromotionTopic entity = mapper.toEntity(request);
    PromotionTopic saved = repository.save(entity);
    log.info("Created PromotionTopic id={} name={}", saved.getId(), saved.getName());
    return mapper.toResponse(saved);
  }

  /**
   * Updates the topic identified by {@code id}. The caller-supplied {@code version} is compared
   * against the loaded entity and a mismatch produces an {@link
   * ObjectOptimisticLockingFailureException} that surfaces as HTTP 409.
   *
   * @param id identifier of the topic to update
   * @param request validated payload with the new field values and the previously fetched {@code
   *     version}
   * @return the updated topic in response form
   * @throws EntityNotFoundException if no topic exists for that id
   * @throws ObjectOptimisticLockingFailureException if the request's {@code version} no longer
   *     matches the persisted entity
   */
  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public PromotionTopicResponse update(
      @NotNull UUID id, @NotNull PromotionTopicUpdateRequest request) {
    PromotionTopic entity = load(id);
    if (!entity.getVersion().equals(request.version())) {
      throw new ObjectOptimisticLockingFailureException(PromotionTopic.class, id);
    }
    mapper.updateEntity(entity, request);
    PromotionTopic saved = repository.save(entity);
    log.info("Updated PromotionTopic id={}", id);
    return mapper.toResponse(saved);
  }

  /**
   * Permanently removes the topic identified by {@code id}, cascading the orphan removal to its
   * categories. Restricted to ADMIN or OFFICER callers.
   *
   * @param id identifier of the topic to delete
   * @throws EntityNotFoundException if no topic exists for that id
   */
  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@NotNull UUID id) {
    PromotionTopic entity = load(id);
    repository.delete(entity);
    log.info("Deleted PromotionTopic id={}", id);
  }

  @NotNull
  private PromotionTopic load(@NotNull UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("PromotionTopic not found: " + id));
  }
}
