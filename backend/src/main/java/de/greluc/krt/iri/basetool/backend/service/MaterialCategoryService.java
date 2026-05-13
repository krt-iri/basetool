package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.MaterialCategory;
import de.greluc.krt.iri.basetool.backend.repository.MaterialCategoryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD service for the {@code material_category} reference table.
 *
 * <p>Categories are the admin-defined grouping above {@link
 * de.greluc.krt.iri.basetool.backend.model.Material} (e.g. "Refinable Ore", "Manufactured Good") —
 * independent of the UEX-imported flags. The list is always sorted alphabetically because the
 * frontend renders it directly into dropdowns without sorting again.
 */
@Service
@RequiredArgsConstructor
public class MaterialCategoryService {

  private final MaterialCategoryRepository repository;

  /**
   * @return all categories sorted by name ascending
   */
  public List<MaterialCategory> findAll() {
    return repository.findAll(Sort.by("name").ascending());
  }

  /**
   * @param id category primary key
   * @return the category
   * @throws NotFoundException when no category matches
   */
  public MaterialCategory findById(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("MaterialCategory not found"));
  }

  /**
   * Persists a new category.
   *
   * @param category transient entity
   * @return the persisted category
   */
  @Transactional
  public MaterialCategory create(MaterialCategory category) {
    return repository.save(category);
  }

  /**
   * Updates the name of an existing category. Only {@code name} is mutable; description and
   * structural fields stay untouched.
   *
   * @param id category primary key
   * @param category transient entity carrying the new name
   * @return the persisted category
   */
  @Transactional
  public MaterialCategory update(UUID id, MaterialCategory category) {
    MaterialCategory existing = findById(id);
    existing.setName(category.getName());
    return repository.save(existing);
  }

  /**
   * Deletes a category. The backend rejects the delete with {@link
   * de.greluc.krt.iri.basetool.backend.exception.EntityInUseException} when any material still
   * references the category.
   *
   * @param id category primary key
   */
  @Transactional
  public void delete(UUID id) {
    MaterialCategory existing = findById(id);
    repository.delete(existing);
  }
}
