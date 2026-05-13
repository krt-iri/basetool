package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.config.CacheConfig;
import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronDto;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cached CRUD service for the {@code squadron} reference table.
 *
 * <p>Squadrons are the organizational units a user can be assigned to (each user belongs to one
 * squadron at a time; mission participants may name a different squadron for that mission's roster
 * line). Same soft-delete + case-insensitive uniqueness pattern as {@link JobTypeService}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SquadronService {

  private final SquadronRepository squadronRepository;
  private final MissionParticipantRepository missionParticipantRepository;

  /**
   * Unpaged squadron list for dropdowns.
   *
   * @param includeInactive when true, include soft-deleted entries
   * @return cached list
   */
  @Cacheable(cacheNames = CacheConfig.SQUADRONS_CACHE)
  public List<Squadron> getAllSquadrons(boolean includeInactive) {
    return includeInactive
        ? squadronRepository.findAll()
        : squadronRepository.findAllByActiveTrue();
  }

  /**
   * Paged variant for the admin list.
   *
   * @param pageable page request
   * @param includeInactive when true, include soft-deleted entries
   * @return cached page
   */
  @Cacheable(cacheNames = CacheConfig.SQUADRONS_CACHE)
  public Page<Squadron> getAllSquadrons(@NotNull Pageable pageable, boolean includeInactive) {
    return includeInactive
        ? squadronRepository.findAll(pageable)
        : squadronRepository.findAllByActiveTrue(pageable);
  }

  /**
   * @param id squadron primary key
   * @return the squadron
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no match
   */
  public Squadron getSquadronById(@NotNull UUID id) {
    return squadronRepository
        .findById(id)
        .orElseThrow(
            () ->
                new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                    "Squadron not found"));
  }

  /**
   * Persists a new squadron. Case-insensitive duplicate name throws {@link
   * DuplicateEntityException}.
   *
   * @param squadron transient entity
   * @return the persisted squadron
   */
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.SQUADRONS_CACHE, allEntries = true)
  public Squadron createSquadron(@NotNull Squadron squadron) {
    if (squadronRepository.existsByNameIgnoreCase(squadron.getName())) {
      throw new DuplicateEntityException(
          "A Squadron with the name '" + squadron.getName() + "' already exists.");
    }
    return squadronRepository.save(squadron);
  }

  /**
   * Updates an existing squadron with optimistic-lock and duplicate-name checks.
   *
   * @param id squadron primary key
   * @param squadronDto update payload
   * @return the persisted squadron
   * @throws DuplicateEntityException when the new name collides with a different row
   * @throws ObjectOptimisticLockingFailureException when the supplied version is stale
   */
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.SQUADRONS_CACHE, allEntries = true)
  public Squadron updateSquadron(@NotNull UUID id, @NotNull SquadronDto squadronDto) {
    if (squadronRepository.existsByNameIgnoreCaseAndIdNot(squadronDto.name(), id)) {
      throw new DuplicateEntityException(
          "A Squadron with the name '" + squadronDto.name() + "' already exists.");
    }
    Squadron squadron = getSquadronById(id);

    if (squadron.getVersion() != null && !squadron.getVersion().equals(squadronDto.version())) {
      throw new ObjectOptimisticLockingFailureException(Squadron.class, id);
    }

    squadron.setName(squadronDto.name());
    squadron.setShorthand(squadronDto.shorthand());
    squadron.setDescription(squadronDto.description());
    return squadronRepository.save(squadron);
  }

  /**
   * Soft-deletes a squadron by flipping {@code active=false}.
   *
   * @param id squadron primary key
   */
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.SQUADRONS_CACHE, allEntries = true)
  public void deleteSquadron(@NotNull UUID id) {
    if (!squadronRepository.existsById(id)) {
      throw new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
          "Squadron not found");
    }
    Squadron squadron = getSquadronById(id);
    squadron.setActive(false);
    squadronRepository.save(squadron);
  }

  /**
   * Reverses a soft-delete.
   *
   * @param id squadron primary key
   */
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.SQUADRONS_CACHE, allEntries = true)
  public void activateSquadron(@NotNull UUID id) {
    Squadron squadron = getSquadronById(id);
    squadron.setActive(true);
    squadronRepository.save(squadron);
  }
}
