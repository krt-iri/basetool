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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Transactional
public class LocationController {

  private final LocationService locationService;
  private final LocationMapper locationMapper;

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

  @GetMapping("/lookup")
  public List<de.greluc.krt.iri.basetool.backend.model.dto.LocationReferenceDto> lookupLocations() {
    return locationService.findAllReference();
  }

  @GetMapping("/{id}")
  public LocationDto getLocation(@PathVariable @NotNull UUID id) {
    return locationMapper.toDto(locationService.getLocation(id));
  }

  @GetMapping("/refineries")
  @PreAuthorize("isAuthenticated()")
  public List<LocationDto> getRefineryLocations() {
    return locationService.getRefineryLocations().stream().map(locationMapper::toDto).toList();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
  public LocationDto createLocation(@RequestBody @Valid @NotNull LocationDto location) {
    // stripServerManaged drops client-supplied id / version so JPA performs an INSERT
    // and the client cannot mass-assign onto an existing row through this endpoint.
    Location entity = LocationMapper.stripServerManaged(locationMapper.toEntity(location));
    return locationMapper.toDto(locationService.createLocation(entity));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
  public LocationDto updateLocation(
      @PathVariable @NotNull UUID id, @RequestBody @Valid @NotNull LocationDto location) {
    return locationMapper.toDto(locationService.updateLocation(id, location));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
  public void deleteLocation(@PathVariable @NotNull UUID id) {
    locationService.deleteLocation(id);
  }
}
