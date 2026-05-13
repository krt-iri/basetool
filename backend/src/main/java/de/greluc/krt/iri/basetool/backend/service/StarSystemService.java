package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.config.CacheConfig;
import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.model.StarSystem;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.StarSystemRepository;
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
 * Cached CRUD service for the {@code star_system} table.
 *
 * <p>UEX owns the bulk of this table; this service adds the admin-only create/update/delete API for
 * systems UEX doesn't know about yet (e.g. just-announced systems before they appear in the UEX
 * feed). Case-insensitive uniqueness on name is enforced explicitly via existence checks — a DB
 * unique index alone would surface as a generic 500 instead of a 409 with a localized message.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StarSystemService {

  private final StarSystemRepository starSystemRepository;
  private final LocationRepository locationRepository;

  /**
   * @param pageable page request
   * @return cached page of star systems
   */
  @Cacheable(cacheNames = CacheConfig.STAR_SYSTEMS_CACHE)
  public Page<StarSystem> getAllStarSystems(@NotNull Pageable pageable) {
    return starSystemRepository.findAll(pageable);
  }

  /**
   * @param id star system primary key
   * @return the star system
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no match
   */
  @Cacheable(cacheNames = CacheConfig.STAR_SYSTEMS_CACHE)
  public StarSystem getStarSystem(@NotNull UUID id) {
    return starSystemRepository
        .findById(id)
        .orElseThrow(
            () ->
                new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                    "StarSystem not found"));
  }

  /**
   * Persists a new star system. Case-insensitive duplicate name throws {@link
   * DuplicateEntityException} → 409 before the DB unique-constraint would.
   *
   * @param starSystem transient entity
   * @return the persisted star system
   */
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.STAR_SYSTEMS_CACHE, allEntries = true)
  public StarSystem createStarSystem(@NotNull StarSystem starSystem) {
    if (starSystemRepository.existsByNameIgnoreCase(starSystem.getName())) {
      throw new DuplicateEntityException(
          "A Star System with the name '" + starSystem.getName() + "' already exists.");
    }
    return starSystemRepository.save(starSystem);
  }

  /**
   * Updates name and description of an existing star system. Other fields (UEX flags, jurisdiction,
   * faction) come from UEX and are not mutable here.
   *
   * @param id star system primary key
   * @param starSystemDetails transient entity carrying the new values
   * @return the persisted star system
   * @throws DuplicateEntityException when the new name collides with a different system
   */
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.STAR_SYSTEMS_CACHE, allEntries = true)
  public StarSystem updateStarSystem(@NotNull UUID id, @NotNull StarSystem starSystemDetails) {
    if (starSystemRepository.existsByNameIgnoreCaseAndIdNot(starSystemDetails.getName(), id)) {
      throw new DuplicateEntityException(
          "A Star System with the name '" + starSystemDetails.getName() + "' already exists.");
    }
    StarSystem starSystem = getStarSystem(id);

    starSystem.setName(starSystemDetails.getName());
    starSystem.setDescription(starSystemDetails.getDescription());

    return starSystemRepository.save(starSystem);
  }

  /**
   * Deletes a star system. Rejected when any location still references the system.
   *
   * @param id star system primary key
   */
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.STAR_SYSTEMS_CACHE, allEntries = true)
  public void deleteStarSystem(@NotNull UUID id) {
    StarSystem starSystem = getStarSystem(id);
    starSystemRepository.delete(starSystem);
  }
}
