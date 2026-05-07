package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.exception.EntityInUseException;
import de.greluc.krt.iri.basetool.backend.model.StarSystem;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.StarSystemRepository;
import de.greluc.krt.iri.basetool.backend.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StarSystemService {

    private final StarSystemRepository starSystemRepository;
    private final LocationRepository locationRepository;

    @Cacheable(cacheNames = CacheConfig.STAR_SYSTEMS_CACHE)
    public Page<StarSystem> getAllStarSystems(@NotNull Pageable pageable) {
        return starSystemRepository.findAll(pageable);
    }

    @Cacheable(cacheNames = CacheConfig.STAR_SYSTEMS_CACHE)
    public StarSystem getStarSystem(@NotNull UUID id) {
        return starSystemRepository.findById(id)
            .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("StarSystem not found"));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.STAR_SYSTEMS_CACHE, allEntries = true)
    public StarSystem createStarSystem(@NotNull StarSystem starSystem) {
        if (starSystemRepository.existsByNameIgnoreCase(starSystem.getName())) {
            throw new DuplicateEntityException("A Star System with the name '" + starSystem.getName() + "' already exists.");
        }
        return starSystemRepository.save(starSystem);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.STAR_SYSTEMS_CACHE, allEntries = true)
    public StarSystem updateStarSystem(@NotNull UUID id, @NotNull StarSystem starSystemDetails) {
        if (starSystemRepository.existsByNameIgnoreCaseAndIdNot(starSystemDetails.getName(), id)) {
            throw new DuplicateEntityException("A Star System with the name '" + starSystemDetails.getName() + "' already exists.");
        }
        StarSystem starSystem = getStarSystem(id);
        
        starSystem.setName(starSystemDetails.getName());
        starSystem.setDescription(starSystemDetails.getDescription());
        
        return starSystemRepository.save(starSystem);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.STAR_SYSTEMS_CACHE, allEntries = true)
    public void deleteStarSystem(@NotNull UUID id) {
        StarSystem starSystem = getStarSystem(id);
        starSystemRepository.delete(starSystem);
    }
}
