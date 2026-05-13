package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.config.CacheConfig;
import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.repository.ManufacturerRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManufacturerService {

  private final ManufacturerRepository manufacturerRepository;
  private final ShipTypeRepository shipTypeRepository;

  @Cacheable(cacheNames = CacheConfig.MANUFACTURERS_CACHE)
  public Page<Manufacturer> getAllManufacturers(@NotNull Pageable pageable, boolean includeHidden) {
    if (includeHidden) {
      return manufacturerRepository.findAll(pageable);
    }
    return manufacturerRepository.findByHiddenFalse(pageable);
  }

  @Cacheable(cacheNames = CacheConfig.MANUFACTURERS_CACHE)
  public Manufacturer getManufacturer(@NotNull UUID id) {
    return manufacturerRepository
        .findById(id)
        .orElseThrow(
            () ->
                new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                    "Manufacturer not found"));
  }

  @Transactional
  @CacheEvict(cacheNames = CacheConfig.MANUFACTURERS_CACHE, allEntries = true)
  public Manufacturer updateManufacturerVisibility(@NotNull UUID id, boolean hidden) {
    Manufacturer manufacturer = getManufacturer(id);
    manufacturer.setHidden(hidden);
    return manufacturerRepository.save(manufacturer);
  }
}
