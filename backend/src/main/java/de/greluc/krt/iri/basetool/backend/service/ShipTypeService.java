package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.exception.EntityInUseException;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.repository.ManufacturerRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
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
public class ShipTypeService {

    private final ShipTypeRepository shipTypeRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final ShipRepository shipRepository;

    @Cacheable(cacheNames = CacheConfig.SHIP_TYPES_CACHE)
    public Page<ShipType> getAllShipTypes(@NotNull Pageable pageable, boolean includeHidden) {
        if (includeHidden) {
            return shipTypeRepository.findAll(pageable);
        }
        return shipTypeRepository.findByHiddenFalse(pageable);
    }

    @Cacheable(cacheNames = CacheConfig.SHIP_TYPES_CACHE)
    public ShipType getShipType(@NotNull UUID id) {
        return shipTypeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("ShipType not found"));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.SHIP_TYPES_CACHE, allEntries = true)
    public ShipType updateShipTypeVisibility(@NotNull UUID id, boolean hidden) {
        ShipType shipType = getShipType(id);
        shipType.setHidden(hidden);
        return shipTypeRepository.save(shipType);
    }

    private void resolveManufacturer(ShipType shipType) {
        if (shipType.getManufacturer() != null && shipType.getManufacturer().getId() != null) {
            shipType.setManufacturer(manufacturerRepository.findById(shipType.getManufacturer().getId())
                    .orElseThrow(() -> new RuntimeException("Manufacturer not found")));
        } else {
            shipType.setManufacturer(null);
        }
    }
}
