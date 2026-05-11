package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.MaterialCategory;
import de.greluc.krt.iri.basetool.backend.repository.MaterialCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
@Service
@RequiredArgsConstructor
public class MaterialCategoryService {

    private final MaterialCategoryRepository repository;

    public List<MaterialCategory> findAll() {
        return repository.findAll(Sort.by("name").ascending());
    }

    public MaterialCategory findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("MaterialCategory not found"));
    }

    @Transactional
    public MaterialCategory create(MaterialCategory category) {
        return repository.save(category);
    }

    @Transactional
    public MaterialCategory update(UUID id, MaterialCategory category) {
        MaterialCategory existing = findById(id);
        existing.setName(category.getName());
        return repository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        MaterialCategory existing = findById(id);
        repository.delete(existing);
    }
}
