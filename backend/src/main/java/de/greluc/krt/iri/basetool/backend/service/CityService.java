package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.City;
import de.greluc.krt.iri.basetool.backend.repository.CityRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read service plus admin-override mutators for the city catalogue. The records themselves are
 * owned by {@link UexUniverseSyncService}; this service only exposes the read API and the
 * admin-only {@code hasLoadingDock} pin used by the UEX-overrides admin page.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityService {

  private final CityRepository cityRepository;

  /**
   * Returns the paged city catalogue.
   *
   * @param pageable page request
   * @return one page of cities, sorted by the pageable's sort
   */
  public Page<City> getAllCities(Pageable pageable) {
    return cityRepository.findAll(pageable);
  }

  /**
   * Returns a single city by primary key.
   *
   * @param id city primary key
   * @return the managed city entity
   * @throws NotFoundException when no city matches the id
   */
  public City getCity(UUID id) {
    return cityRepository.findById(id).orElseThrow(() -> new NotFoundException("City not found"));
  }

  /**
   * Pins {@code hasLoadingDock} to the supplied value and marks the row as admin-overridden so the
   * next UEX sweep leaves the value column untouched.
   *
   * @param id city primary key
   * @param value desired {@code hasLoadingDock} value
   * @return the persisted city
   */
  @Transactional
  public City setLoadingDockOverride(UUID id, boolean value) {
    City city = getCity(id);
    city.setHasLoadingDock(value);
    city.setHasLoadingDockOverridden(true);
    return cityRepository.save(city);
  }

  /**
   * Releases the admin pin on {@code hasLoadingDock}. The value column stays at its last value
   * until the next UEX sweep overwrites it from the upstream feed.
   *
   * @param id city primary key
   * @return the persisted city
   */
  @Transactional
  public City clearLoadingDockOverride(UUID id) {
    City city = getCity(id);
    city.setHasLoadingDockOverridden(false);
    return cityRepository.save(city);
  }
}
