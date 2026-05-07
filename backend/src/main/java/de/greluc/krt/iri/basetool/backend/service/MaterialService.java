package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialCategory;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialMatrixItemDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialPriceOverviewDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialPriceDto;
import de.greluc.krt.iri.basetool.backend.repository.MaterialCategoryRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialPriceRepository;
import de.greluc.krt.iri.basetool.backend.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialPriceRepository materialPriceRepository;
    private final MaterialCategoryRepository materialCategoryRepository;

    @Cacheable(cacheNames = CacheConfig.MATERIALS_CACHE)
    public Page<Material> getAllMaterials(@NotNull Pageable pageable) {
        return materialRepository.findAll(pageable);
    }

    public Page<Material> getAllMaterialsWithPrices(@NotNull Pageable pageable) {
        return materialRepository.findAllWithPrices(pageable);
    }

    public Page<MaterialPriceOverviewDto> getMaterialPriceOverview(@NotNull String name, @NotNull Pageable pageable) {
        return materialRepository.getMaterialPriceOverview(name, pageable);
    }

    public List<de.greluc.krt.iri.basetool.backend.model.dto.MaterialReferenceDto> findAllReference() {
        return materialRepository.findAllReference();
    }

    @Cacheable(cacheNames = CacheConfig.MATERIALS_CACHE)
    public Material getMaterial(@NotNull UUID id) {
        return materialRepository.findById(id)
            .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Material not found"));
    }

    public Page<MaterialPriceDto> getMaterialPrices(@NotNull UUID id, @NotNull Pageable pageable) {
        return materialPriceRepository.findPricesByMaterialId(id, pageable);
    }

    public List<de.greluc.krt.iri.basetool.backend.model.dto.MaterialSellingTerminalDto> getMaterialTerminals(@NotNull UUID id) {
        return materialPriceRepository.findSellingTerminalsByMaterialId(id);
    }

    public Page<MaterialMatrixItemDto> getAllMatrixItems(@NotNull Pageable pageable) {
        return materialPriceRepository.findAllMatrixItems(pageable);
    }

    public List<Material> getAllJobOrderMaterials() {
        return materialRepository.findAllByIsJobOrderTrueOrderByNameAsc();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.MATERIALS_CACHE, allEntries = true)
    public Material createMaterial(@NotNull Material material) {
        return materialRepository.save(material);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.MATERIALS_CACHE, allEntries = true)
    public Material updateMaterial(@NotNull UUID id, @NotNull Material materialDetails) {
        Material material = getMaterial(id);
        
        if (materialDetails.getVersion() != null && !materialDetails.getVersion().equals(material.getVersion())) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Material.class, id);
        }
        
        material.setName(materialDetails.getName());
        material.setType(materialDetails.getType());
        material.setDescription(materialDetails.getDescription());
        material.setQuantityType(materialDetails.getQuantityType());
        material.setIsManualRawMaterial(materialDetails.getIsManualRawMaterial());
        material.setIsJobOrder(materialDetails.getIsJobOrder());
        
        if (materialDetails.getRefinedMaterial() != null && materialDetails.getRefinedMaterial().getId() != null) {
            Material refined = materialRepository.findById(materialDetails.getRefinedMaterial().getId()).orElse(null);
            material.setRefinedMaterial(refined);
        } else {
            material.setRefinedMaterial(null);
        }
        
        if (materialDetails.getCategory() != null && materialDetails.getCategory().getId() != null) {
            MaterialCategory category = materialCategoryRepository.findById(materialDetails.getCategory().getId()).orElse(null);
            material.setCategory(category);
        } else {
            material.setCategory(null);
        }
        
        return materialRepository.save(material);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.MATERIALS_CACHE, allEntries = true)
    public void deleteMaterial(@NotNull UUID id) {
        Material material = getMaterial(id);
        materialRepository.delete(material);
    }
}
