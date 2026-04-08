package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.config.CacheConfig;
import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.model.JobType;
import de.greluc.krt.iri.basetool.backend.model.JobTypeArchetype;
import de.greluc.krt.iri.basetool.backend.model.dto.JobTypeDto;
import de.greluc.krt.iri.basetool.backend.repository.JobTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionCrewRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
public class JobTypeService {

    private final JobTypeRepository jobTypeRepository;
    private final MissionCrewRepository missionCrewRepository;
    private final MissionParticipantRepository missionParticipantRepository;

    @Cacheable(cacheNames = CacheConfig.JOB_TYPES_CACHE)
    public List<JobType> getJobTypes(@Nullable JobTypeArchetype archetype) {
        if (archetype == null) {
            return jobTypeRepository.findByActiveTrue();
        }
        return jobTypeRepository.findByArchetypeAndActiveTrue(archetype);
    }

    @Cacheable(cacheNames = CacheConfig.JOB_TYPES_CACHE)
    public Page<JobType> getJobTypes(@Nullable JobTypeArchetype archetype, @NotNull Pageable pageable, boolean includeInactive) {
        if (archetype == null) {
            return includeInactive ? jobTypeRepository.findAll(pageable) : jobTypeRepository.findByActiveTrue(pageable);
        }
        return includeInactive ? jobTypeRepository.findByArchetype(archetype, pageable) : jobTypeRepository.findByArchetypeAndActiveTrue(archetype, pageable);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.JOB_TYPES_CACHE, allEntries = true)
    public JobType createJobType(@NotNull JobType jobType) {
        if (jobTypeRepository.existsByNameIgnoreCase(jobType.getName())) {
            throw new DuplicateEntityException("A Job Type with the name '" + jobType.getName() + "' already exists.");
        }
        if (jobType.getParent() != null && jobType.getParent().getId() != null) {
             JobType parent = jobTypeRepository.findById(jobType.getParent().getId())
                 .orElseThrow(() -> new RuntimeException("Parent JobType not found"));
             jobType.setParent(parent);
        } else {
            jobType.setParent(null);
        }
        return jobTypeRepository.save(jobType);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.JOB_TYPES_CACHE, allEntries = true)
    public JobType updateJobType(@NotNull UUID id, @NotNull JobTypeDto jobTypeDto) {
        if (jobTypeRepository.existsByNameIgnoreCaseAndIdNot(jobTypeDto.name(), id)) {
            throw new DuplicateEntityException("A Job Type with the name '" + jobTypeDto.name() + "' already exists.");
        }
        JobType jobType = jobTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("JobType not found"));

        if (jobType.getVersion() != null && !jobType.getVersion().equals(jobTypeDto.version())) {
            throw new ObjectOptimisticLockingFailureException(JobType.class, id);
        }

        jobType.setName(jobTypeDto.name());
        jobType.setDescription(jobTypeDto.description());
        jobType.setArchetype(jobTypeDto.archetype());
        jobType.setLeadershipRole(jobTypeDto.isLeadershipRole());
        
        if (jobTypeDto.parentId() != null) {
            JobType parent = jobTypeRepository.findById(jobTypeDto.parentId())
                    .orElseThrow(() -> new RuntimeException("Parent JobType not found"));
            jobType.setParent(parent);
        } else {
            jobType.setParent(null);
        }

        return jobTypeRepository.save(jobType);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.JOB_TYPES_CACHE, allEntries = true)
    public void deleteJobType(@NotNull UUID id) {
        JobType jobTypeToDeactivate = jobTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("JobType not found"));

        jobTypeToDeactivate.setActive(false);
        jobTypeRepository.save(jobTypeToDeactivate);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.JOB_TYPES_CACHE, allEntries = true)
    public void activateJobType(@NotNull UUID id) {
        JobType jobTypeToActivate = jobTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("JobType not found"));

        jobTypeToActivate.setActive(true);
        jobTypeRepository.save(jobTypeToActivate);
    }
}
