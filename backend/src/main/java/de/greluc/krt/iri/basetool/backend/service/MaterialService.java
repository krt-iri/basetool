package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.config.CacheConfig;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialCategory;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialMatrixItemDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialPriceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialPriceOverviewDto;
import de.greluc.krt.iri.basetool.backend.repository.MaterialCategoryRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialPriceRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read service for materials plus the admin-mutable subset of fields.
 *
 * <p>The catalog itself comes from {@link UexCommodityService}; this service owns the
 * project-specific fields that admins maintain by hand: category, refined-material mapping, the
 * manual {@code isJobOrder} and {@code isManualRawMaterial} flags (overrides when UEX gets the
 * refinable/refined classification wrong). Cache eviction is {@code allEntries=true} because the
 * materials catalog drives many downstream views — surgical eviction is not worth the complexity.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialService {

  private final MaterialRepository materialRepository;
  private final MaterialPriceRepository materialPriceRepository;
  private final MaterialCategoryRepository materialCategoryRepository;

  /**
   * @param pageable page request
   * @return cached paged list of all materials
   */
  @Cacheable(cacheNames = CacheConfig.MATERIALS_CACHE)
  public Page<Material> getAllMaterials(@NotNull Pageable pageable) {
    return materialRepository.findAll(pageable);
  }

  /**
   * Variant that eager-fetches the per-terminal price list via {@code @EntityGraph} on the
   * repository — used by views that show prices inline.
   *
   * @param pageable page request
   * @return paged list of materials with prices eagerly loaded
   */
  public Page<Material> getAllMaterialsWithPrices(@NotNull Pageable pageable) {
    return materialRepository.findAllWithPrices(pageable);
  }

  /**
   * Projection used by the materials-overview page: per-material best buy / best sell summary
   * (filtered by name).
   *
   * @param name optional case-insensitive substring filter (empty = all)
   * @param pageable page request
   * @return paged price-overview DTOs
   */
  public Page<MaterialPriceOverviewDto> getMaterialPriceOverview(
      @NotNull String name, @NotNull Pageable pageable) {
    return materialRepository.getMaterialPriceOverview(name, pageable);
  }

  /**
   * Lightweight projection used by typeaheads — only id and name.
   *
   * @return all materials as reference DTOs
   */
  public List<de.greluc.krt.iri.basetool.backend.model.dto.MaterialReferenceDto>
      findAllReference() {
    return materialRepository.findAllReference();
  }

  /**
   * @param id material primary key
   * @return the material
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no match
   */
  @Cacheable(cacheNames = CacheConfig.MATERIALS_CACHE)
  public Material getMaterial(@NotNull UUID id) {
    return materialRepository
        .findById(id)
        .orElseThrow(
            () ->
                new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                    "Material not found"));
  }

  /**
   * Paged per-material price list.
   *
   * @param id material primary key
   * @param pageable page request
   * @return paged price DTOs (one per terminal that trades this material)
   */
  public Page<MaterialPriceDto> getMaterialPrices(@NotNull UUID id, @NotNull Pageable pageable) {
    return materialPriceRepository.findPricesByMaterialId(id, pageable);
  }

  /**
   * Per-material selling-terminal projection used by the inventory page to suggest where to sell.
   *
   * @param id material primary key
   * @return list of selling-terminal DTOs (terminal + sell price)
   */
  public List<de.greluc.krt.iri.basetool.backend.model.dto.MaterialSellingTerminalDto>
      getMaterialTerminals(@NotNull UUID id) {
    return materialPriceRepository.findSellingTerminalsByMaterialId(id);
  }

  /**
   * Returns the full material × terminal matrix used by the matrix overview page. The frontend
   * pulls everything in one query and filters in memory.
   *
   * @param pageable page request
   * @return paged matrix items
   */
  public Page<MaterialMatrixItemDto> getAllMatrixItems(@NotNull Pageable pageable) {
    return materialPriceRepository.findAllMatrixItems(pageable);
  }

  /**
   * Returns only the materials flagged as job-order materials, sorted by name. Used by the
   * job-order create form's material picker.
   *
   * @return job-order materials in alphabetical order
   */
  public List<Material> getAllJobOrderMaterials() {
    return materialRepository.findAllByIsJobOrderTrueOrderByNameAsc();
  }

  /**
   * Persists a manually-created material (rare — admins normally rely on the UEX import).
   *
   * @param material transient entity
   * @return the persisted material
   */
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.MATERIALS_CACHE, allEntries = true)
  public Material createMaterial(@NotNull Material material) {
    return materialRepository.save(material);
  }

  /**
   * Updates the admin-maintained fields on a material (name, type, description, quantity type,
   * manual flags, refined-material link, category). UEX-imported fields are NOT mutable here —
   * those come from {@link UexCommodityService} and any manual override would be silently
   * overwritten on the next sync.
   *
   * <p>Refined-material and category references are resolved by id; unknown ids fall back to {@code
   * null} rather than raising (legacy data may carry stale references).
   *
   * @param id material primary key
   * @param materialDetails transient entity carrying the new values (also the expected version)
   * @return the persisted material
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     version is stale
   */
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.MATERIALS_CACHE, allEntries = true)
  public Material updateMaterial(@NotNull UUID id, @NotNull Material materialDetails) {
    Material material = getMaterial(id);

    if (materialDetails.getVersion() != null
        && !materialDetails.getVersion().equals(material.getVersion())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Material.class, id);
    }

    material.setName(materialDetails.getName());
    material.setType(materialDetails.getType());
    material.setDescription(materialDetails.getDescription());
    material.setQuantityType(materialDetails.getQuantityType());
    material.setIsManualRawMaterial(materialDetails.getIsManualRawMaterial());
    material.setIsJobOrder(materialDetails.getIsJobOrder());

    if (materialDetails.getRefinedMaterial() != null
        && materialDetails.getRefinedMaterial().getId() != null) {
      Material refined =
          materialRepository.findById(materialDetails.getRefinedMaterial().getId()).orElse(null);
      material.setRefinedMaterial(refined);
    } else {
      material.setRefinedMaterial(null);
    }

    if (materialDetails.getCategory() != null && materialDetails.getCategory().getId() != null) {
      MaterialCategory category =
          materialCategoryRepository.findById(materialDetails.getCategory().getId()).orElse(null);
      material.setCategory(category);
    } else {
      material.setCategory(null);
    }

    return materialRepository.save(material);
  }

  /**
   * Deletes a material. Backend rejects the delete when any inventory item or price row still
   * references it (FK constraint surfaces as 409 via the global handler).
   *
   * @param id material primary key
   */
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.MATERIALS_CACHE, allEntries = true)
  public void deleteMaterial(@NotNull UUID id) {
    Material material = getMaterial(id);
    materialRepository.delete(material);
  }
}
