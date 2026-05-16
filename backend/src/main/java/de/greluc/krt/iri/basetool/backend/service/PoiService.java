package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.Poi;
import de.greluc.krt.iri.basetool.backend.repository.PoiRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read service plus admin-override mutators for the POI catalogue. The records themselves are owned
 * by {@link UexUniverseSyncService}; this service only exposes the read API and the admin-only
 * {@code hasLoadingDock} pin used by the UEX-overrides admin page.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PoiService {

  private final PoiRepository poiRepository;

  /**
   * Returns the paged POI catalogue.
   *
   * @param pageable page request
   * @return one page of POIs, sorted by the pageable's sort
   */
  public Page<Poi> getAllPois(Pageable pageable) {
    return poiRepository.findAll(pageable);
  }

  /**
   * Returns a single POI by primary key.
   *
   * @param id POI primary key
   * @return the managed POI entity
   * @throws NotFoundException when no POI matches the id
   */
  public Poi getPoi(UUID id) {
    return poiRepository.findById(id).orElseThrow(() -> new NotFoundException("POI not found"));
  }

  /**
   * Pins {@code hasLoadingDock} to the supplied value and marks the row as admin-overridden so the
   * next UEX sweep leaves the value column untouched.
   *
   * @param id POI primary key
   * @param value desired {@code hasLoadingDock} value
   * @return the persisted POI
   */
  @Transactional
  public Poi setLoadingDockOverride(UUID id, boolean value) {
    Poi poi = getPoi(id);
    poi.setHasLoadingDock(value);
    poi.setHasLoadingDockOverridden(true);
    return poiRepository.save(poi);
  }

  /**
   * Releases the admin pin on {@code hasLoadingDock}. The value column stays at its last value
   * until the next UEX sweep overwrites it from the upstream feed.
   *
   * @param id POI primary key
   * @return the persisted POI
   */
  @Transactional
  public Poi clearLoadingDockOverride(UUID id) {
    Poi poi = getPoi(id);
    poi.setHasLoadingDockOverridden(false);
    return poiRepository.save(poi);
  }
}
