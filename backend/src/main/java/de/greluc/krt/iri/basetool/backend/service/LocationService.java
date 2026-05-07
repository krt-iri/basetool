package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.exception.EntityInUseException;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.dto.LocationDto;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {

    private final LocationRepository locationRepository;
    private final ShipRepository shipRepository;
    private final RefineryOrderRepository refineryOrderRepository;

    @Cacheable(cacheNames = CacheConfig.LOCATIONS_CACHE)
    public Page<Location> getAllLocations(@NotNull Pageable pageable, boolean includeHidden) {
        if (includeHidden) {
            return locationRepository.findAll(pageable);
        }
        return locationRepository.findByHiddenFalse(pageable);
    }

    public List<de.greluc.krt.iri.basetool.backend.model.dto.LocationReferenceDto> findAllReference() {
        return locationRepository.findAllReference();
    }

    @Cacheable(cacheNames = CacheConfig.LOCATIONS_CACHE)
    public Location getLocation(@NotNull UUID id) {
        return locationRepository.findById(id)
            .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Location not found"));
    }

    @Cacheable(cacheNames = CacheConfig.LOCATIONS_CACHE, key = "'refineries'")
    public List<Location> getRefineryLocations() {
        return locationRepository.findLocationsWithRefinery();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.LOCATIONS_CACHE, allEntries = true)
    public Location createLocation(@NotNull Location location) {
        if (locationRepository.existsByNameIgnoreCase(location.getName())) {
            throw new DuplicateEntityException("A Location with the name '" + location.getName() + "' already exists.");
        }
        return locationRepository.save(location);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.LOCATIONS_CACHE, allEntries = true)
    public Location updateLocation(@NotNull UUID id, @NotNull LocationDto locationDto) {
        if (locationRepository.existsByNameIgnoreCaseAndIdNot(locationDto.name(), id)) {
            throw new DuplicateEntityException("A Location with the name '" + locationDto.name() + "' already exists.");
        }
        Location location = getLocation(id);

        if (location.getVersion() != null && !location.getVersion().equals(locationDto.version())) {
            throw new ObjectOptimisticLockingFailureException(Location.class, id);
        }

        location.setName(locationDto.name());
        location.setDescription(locationDto.description());
        location.setHidden(locationDto.hidden());
        
        return locationRepository.save(location);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.LOCATIONS_CACHE, allEntries = true)
    public void deleteLocation(@NotNull UUID id) {
        if (shipRepository.existsByLocationId(id)) {
            throw new EntityInUseException("Cannot delete location: It is currently used by one or more ships.");
        }
        if (refineryOrderRepository.existsByLocationId(id)) {
            throw new EntityInUseException("Cannot delete location: It is currently used by one or more refinery orders.");
        }
        Location location = getLocation(id);
        locationRepository.delete(location);
    }
}
