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

@RestController
@RequestMapping("/api/v1/material-categories")
@RequiredArgsConstructor
public class MaterialCategoryController {

  private final MaterialCategoryService service;
  private final MaterialCategoryMapper mapper;

  @GetMapping
  public List<MaterialCategoryDto> getAll() {
    return service.findAll().stream().map(mapper::toDto).collect(Collectors.toList());
  }

  @GetMapping("/{id}")
  public MaterialCategoryDto getById(@PathVariable UUID id) {
    return mapper.toDto(service.findById(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public MaterialCategoryDto create(@RequestBody MaterialCategoryDto dto) {
    MaterialCategory category = mapper.toEntity(dto);
    MaterialCategory saved = service.create(category);
    return mapper.toDto(saved);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public MaterialCategoryDto update(@PathVariable UUID id, @RequestBody MaterialCategoryDto dto) {
    MaterialCategory category = mapper.toEntity(dto);
    MaterialCategory updated = service.update(id, category);
    return mapper.toDto(updated);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
