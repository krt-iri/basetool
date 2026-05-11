package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.FrequencyType;
import de.greluc.krt.iri.basetool.backend.repository.FrequencyTypeRepository;
import de.greluc.krt.iri.basetool.backend.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.List;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FrequencyTypeService {

    private final FrequencyTypeRepository frequencyTypeRepository;

    public Page<FrequencyType> getAllFrequencyTypes(Boolean active, @NotNull Pageable pageable) {
        return frequencyTypeRepository.findAllByActive(active, pageable);
    }

    public FrequencyType getFrequencyType(@NotNull UUID id) {
        return frequencyTypeRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("FrequencyType not found"));
    }

    @Transactional
    public FrequencyType createFrequencyType(@NotNull FrequencyType frequencyType) {
        return frequencyTypeRepository.save(frequencyType);
    }

    @Transactional
    public FrequencyType updateFrequencyType(@NotNull UUID id, @NotNull FrequencyType frequencyType) {
        FrequencyType existing = getFrequencyType(id);
        existing.setName(frequencyType.getName());
        existing.setDescription(frequencyType.getDescription());
        existing.setActive(frequencyType.isActive());
        return frequencyTypeRepository.save(existing);
    }

    @Transactional
    public void deleteFrequencyType(@NotNull UUID id) {
        if (!frequencyTypeRepository.existsById(id)) {
            throw new NotFoundException("FrequencyType not found");
        }
        FrequencyType existing = getFrequencyType(id);
        existing.setActive(false);
        frequencyTypeRepository.save(existing);
    }

    @Transactional
    public void activateFrequencyType(@NotNull UUID id) {
        FrequencyType existing = getFrequencyType(id);
        existing.setActive(true);
        frequencyTypeRepository.save(existing);
    }

    @Transactional
    public void reorderFrequencyTypes(@NotNull List<UUID> ids) {
        for (int i = 0; i < ids.size(); i++) {
            int index = i;
            frequencyTypeRepository.findById(ids.get(i)).ifPresent(freq -> {
                freq.setSortIndex(index);
                frequencyTypeRepository.save(freq);
            });
        }
    }
}