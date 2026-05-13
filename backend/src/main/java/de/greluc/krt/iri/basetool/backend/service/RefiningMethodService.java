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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefiningMethodService {

  private final RefiningMethodRepository refiningMethodRepository;

  @Cacheable(cacheNames = CacheConfig.REFINING_METHODS_CACHE)
  public Page<RefiningMethod> getAllRefiningMethods(@NotNull Pageable pageable) {
    return refiningMethodRepository.findAll(pageable);
  }

  @Cacheable(cacheNames = CacheConfig.REFINING_METHODS_CACHE)
  public RefiningMethod getRefiningMethod(@NotNull UUID id) {
    return refiningMethodRepository
        .findById(id)
        .orElseThrow(
            () ->
                new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                    "RefiningMethod not found"));
  }

  @Transactional
  @CacheEvict(cacheNames = CacheConfig.REFINING_METHODS_CACHE, allEntries = true)
  public RefiningMethod createRefiningMethod(@NotNull RefiningMethod refiningMethod) {
    return refiningMethodRepository.save(refiningMethod);
  }

  @Transactional
  @CacheEvict(cacheNames = CacheConfig.REFINING_METHODS_CACHE, allEntries = true)
  public RefiningMethod updateRefiningMethod(
      @NotNull UUID id, @NotNull RefiningMethod refiningMethodDetails) {
    RefiningMethod refiningMethod = getRefiningMethod(id);

    refiningMethod.setName(refiningMethodDetails.getName());
    refiningMethod.setDescription(refiningMethodDetails.getDescription());

    return refiningMethodRepository.save(refiningMethod);
  }

  @Transactional
  @CacheEvict(cacheNames = CacheConfig.REFINING_METHODS_CACHE, allEntries = true)
  public void deleteRefiningMethod(@NotNull UUID id) {
    RefiningMethod refiningMethod = getRefiningMethod(id);
    refiningMethodRepository.delete(refiningMethod);
  }
}
