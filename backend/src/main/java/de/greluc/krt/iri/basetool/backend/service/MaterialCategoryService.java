package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.MaterialCategory;
import de.greluc.krt.iri.basetool.backend.repository.MaterialCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaterialCategoryService {

    private final MaterialCategoryRepository repository;

    public List<MaterialCategory> findAll() {
        return repository.findAll(Sort.by("name").ascending());
    }

    public MaterialCategory findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MaterialCategory not found"));
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
