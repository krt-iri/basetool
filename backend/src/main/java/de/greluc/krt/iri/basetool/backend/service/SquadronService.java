package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.config.CacheConfig;
import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.exception.EntityInUseException;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronDto;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SquadronService {

    private final SquadronRepository squadronRepository;
    private final MissionParticipantRepository missionParticipantRepository;

    @Cacheable(cacheNames = CacheConfig.SQUADRONS_CACHE)
    public List<Squadron> getAllSquadrons(boolean includeInactive) {
        return includeInactive ? squadronRepository.findAll() : squadronRepository.findAllByActiveTrue();
    }

    @Cacheable(cacheNames = CacheConfig.SQUADRONS_CACHE)
    public Page<Squadron> getAllSquadrons(@NotNull Pageable pageable, boolean includeInactive) {
        return includeInactive ? squadronRepository.findAll(pageable) : squadronRepository.findAllByActiveTrue(pageable);
    }

    public Squadron getSquadronById(@NotNull UUID id) {
        return squadronRepository.findById(id)
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Squadron not found"));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.SQUADRONS_CACHE, allEntries = true)
    public Squadron createSquadron(@NotNull Squadron squadron) {
        if (squadronRepository.existsByNameIgnoreCase(squadron.getName())) {
            throw new DuplicateEntityException("A Squadron with the name '" + squadron.getName() + "' already exists.");
        }
        return squadronRepository.save(squadron);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.SQUADRONS_CACHE, allEntries = true)
    public Squadron updateSquadron(@NotNull UUID id, @NotNull SquadronDto squadronDto) {
        if (squadronRepository.existsByNameIgnoreCaseAndIdNot(squadronDto.name(), id)) {
            throw new DuplicateEntityException("A Squadron with the name '" + squadronDto.name() + "' already exists.");
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

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.SQUADRONS_CACHE, allEntries = true)
    public void deleteSquadron(@NotNull UUID id) {
        if (!squadronRepository.existsById(id)) {
            throw new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Squadron not found");
        }
        Squadron squadron = getSquadronById(id);
        squadron.setActive(false);
        squadronRepository.save(squadron);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.SQUADRONS_CACHE, allEntries = true)
    public void activateSquadron(@NotNull UUID id) {
        Squadron squadron = getSquadronById(id);
        squadron.setActive(true);
        squadronRepository.save(squadron);
    }
}
