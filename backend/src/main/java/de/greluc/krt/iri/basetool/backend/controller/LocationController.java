package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.LocationMapper;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.dto.LocationDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.service.LocationService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the location reference table. Public endpoint set; mutations are OFFICER/ADMIN.
 * Provides a lightweight {@code /lookup} projection for typeaheads and a dedicated {@code
 * /refineries} list used by the refinery-order create form.
 */
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Transactional
public class LocationController {

  private final LocationService locationService;
  private final LocationMapper locationMapper;

  /**
   * Paged list with {@code includeHidden} for the admin view.
   *
   * @return paged location DTOs
   */
  @GetMapping
  public PageResponse<LocationDto> getAllLocations(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false, defaultValue = "false") boolean includeHidden) {
    Pageable pageable =
        PaginationUtil.createPageRequest(page, size, sort, Set.of("name", "id"), "name");
    Page<Location> p = locationService.getAllLocations(pageable, includeHidden);
    List<LocationDto> content = p.getContent().stream().map(locationMapper::toDto).toList();
    return new PageResponse<>(
        content,
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  /**
   * Lightweight projection for typeaheads — only id and name.
   *
   * @return all locations as reference DTOs
   */
  @GetMapping("/lookup")
  public List<de.greluc.krt.iri.basetool.backend.model.dto.LocationReferenceDto> lookupLocations() {
    return locationService.findAllReference();
  }

  /**
   * Returns the location DTO.
   *
   * @param id location id
   * @return the location DTO
   */
  @GetMapping("/{id}")
  public LocationDto getLocation(@PathVariable @NotNull UUID id) {
    return locationMapper.toDto(locationService.getLocation(id));
  }

  /**
   * Only the locations that host a refinery — used by the refinery-order create form.
   *
   * @return refinery-hosting locations
   */
  @GetMapping("/refineries")
  @PreAuthorize("isAuthenticated()")
  public List<LocationDto> getRefineryLocations() {
    return locationService.getRefineryLocations().stream().map(locationMapper::toDto).toList();
  }

  /**
   * Creates a new location. {@link
   * de.greluc.krt.iri.basetool.backend.mapper.LocationMapper#stripServerManaged} removes
   * client-supplied {@code id}/{@code version} so the client cannot mass-assign onto an existing
   * row.
   *
   * @param location create payload
   * @return the persisted DTO
   */
  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public LocationDto createLocation(@RequestBody @Valid @NotNull LocationDto location) {
    // stripServerManaged drops client-supplied id / version so JPA performs an INSERT
    // and the client cannot mass-assign onto an existing row through this endpoint.
    Location entity = LocationMapper.stripServerManaged(locationMapper.toEntity(location));
    return locationMapper.toDto(locationService.createLocation(entity));
  }

  /**
   * Updates an existing location.
   *
   * @param id location id
   * @param location update payload (carries the expected version)
   * @return the persisted DTO
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public LocationDto updateLocation(
      @PathVariable @NotNull UUID id, @RequestBody @Valid @NotNull LocationDto location) {
    return locationMapper.toDto(locationService.updateLocation(id, location));
  }

  /**
   * Deletes a location. Rejected with {@link
   * de.greluc.krt.iri.basetool.backend.exception.EntityInUseException} when any ship or refinery
   * order still references it.
   *
   * @param id location id
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public void deleteLocation(@PathVariable @NotNull UUID id) {
    locationService.deleteLocation(id);
  }
}
