package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.MaterialCategoryMapper;
import de.greluc.krt.iri.basetool.backend.model.MaterialCategory;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialCategoryDto;
import de.greluc.krt.iri.basetool.backend.service.MaterialCategoryService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the material-category reference table. Read is public; mutations are
 * ADMIN/OFFICER only.
 */
@RestController
@RequestMapping("/api/v1/material-categories")
@RequiredArgsConstructor
public class MaterialCategoryController {

  private final MaterialCategoryService service;
  private final MaterialCategoryMapper mapper;

  /**
   * Returns all categories sorted alphabetically.
   *
   * @return all categories sorted alphabetically
   */
  @GetMapping
  public List<MaterialCategoryDto> getAll() {
    return service.findAll().stream().map(mapper::toDto).collect(Collectors.toList());
  }

  /**
   * Returns the category DTO.
   *
   * @param id category id
   * @return the category DTO
   */
  @GetMapping("/{id}")
  public MaterialCategoryDto getById(@PathVariable UUID id) {
    return mapper.toDto(service.findById(id));
  }

  /**
   * Creates a new category.
   *
   * @param dto create payload
   * @return the persisted DTO
   */
  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public MaterialCategoryDto create(@RequestBody MaterialCategoryDto dto) {
    MaterialCategory category = mapper.toEntity(dto);
    // L-7: strip client-supplied id/version so create cannot become a merge()-UPSERT of another
    // row.
    category.setId(null);
    category.setVersion(null);
    MaterialCategory saved = service.create(category);
    return mapper.toDto(saved);
  }

  /**
   * Updates an existing category.
   *
   * @param id category id
   * @param dto update payload
   * @return the persisted DTO
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public MaterialCategoryDto update(@PathVariable UUID id, @RequestBody MaterialCategoryDto dto) {
    MaterialCategory category = mapper.toEntity(dto);
    MaterialCategory updated = service.update(id, category);
    return mapper.toDto(updated);
  }

  /**
   * Deletes a category. Rejected with 409 when any material still references it.
   *
   * @param id category id
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
