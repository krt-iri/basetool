package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.dto.LocationDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface LocationMapper {
  LocationDto toDto(Location entity);

  Location toEntity(LocationDto dto);

  /**
   * Strips server-managed fields from a freshly mapped entity for the POST/create flow so a client
   * cannot pre-set them via the request body (mass-assignment / over-posting). {@code id} is left
   * null so JPA performs an INSERT instead of a merge against an existing row; {@code version} is
   * left null so the persistence provider initializes the optimistic-locking counter.
   *
   * <p>Declared as a static helper rather than a default mapping method so MapStruct does not
   * consider it a candidate for nested {@code LocationDto -> Location} mappings inside other
   * mappers.
   */
  static Location stripServerManaged(Location entity) {
    if (entity != null) {
      entity.setId(null);
      entity.setVersion(null);
    }
    return entity;
  }
}
