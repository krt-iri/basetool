package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.config.CacheConfig;
import de.greluc.krt.iri.basetool.backend.model.RefiningMethod;
import de.greluc.krt.iri.basetool.backend.repository.RefiningMethodRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cached CRUD service for the {@code refining_method} reference table.
 *
 * <p>Underlying data is owned by {@link UexRefinerySyncService}; this service adds the
 * cache-evicting CRUD surface used by admins to manually correct names / descriptions when UEX data
 * is wrong. The cache eviction is {@code allEntries=true} because the frontend's refining-method
 * dropdown lists everything in one call.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefiningMethodService {

  private final RefiningMethodRepository refiningMethodRepository;

  /**
   * Returns cached page of refining methods.
   *
   * @param pageable page request
   * @return cached page of refining methods
   */
  @Cacheable(cacheNames = CacheConfig.REFINING_METHODS_CACHE)
  public Page<RefiningMethod> getAllRefiningMethods(@NotNull Pageable pageable) {
    return refiningMethodRepository.findAll(pageable);
  }

  /**
   * Returns the refining method.
   *
   * @param id refining method primary key
   * @return the refining method
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no match
   */
  @Cacheable(cacheNames = CacheConfig.REFINING_METHODS_CACHE)
  public RefiningMethod getRefiningMethod(@NotNull UUID id) {
    return refiningMethodRepository
        .findById(id)
        .orElseThrow(
            () ->
                new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                    "RefiningMethod not found"));
  }

  /**
   * Persists a new refining method and evicts the cache.
   *
   * @param refiningMethod transient entity
   * @return the persisted entity
   */
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.REFINING_METHODS_CACHE, allEntries = true)
  public RefiningMethod createRefiningMethod(@NotNull RefiningMethod refiningMethod) {
    return refiningMethodRepository.save(refiningMethod);
  }

  /**
   * Updates name and description of an existing refining method. UEX-imported numeric ratings
   * (yield/cost/speed) are NOT mutable here — those come from {@link UexRefinerySyncService} and a
   * manual override would be silently overwritten on the next sync.
   *
   * @param id refining method primary key
   * @param refiningMethodDetails transient entity carrying the new values
   * @return the persisted entity
   */
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.REFINING_METHODS_CACHE, allEntries = true)
  public RefiningMethod updateRefiningMethod(
      @NotNull UUID id, @NotNull RefiningMethod refiningMethodDetails) {
    RefiningMethod refiningMethod = getRefiningMethod(id);

    refiningMethod.setName(refiningMethodDetails.getName());
    refiningMethod.setDescription(refiningMethodDetails.getDescription());

    return refiningMethodRepository.save(refiningMethod);
  }

  /**
   * Deletes a refining method. The backend rejects the delete when any refinery order still
   * references the method.
   *
   * @param id refining method primary key
   */
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.REFINING_METHODS_CACHE, allEntries = true)
  public void deleteRefiningMethod(@NotNull UUID id) {
    RefiningMethod refiningMethod = getRefiningMethod(id);
    refiningMethodRepository.delete(refiningMethod);
  }
}
