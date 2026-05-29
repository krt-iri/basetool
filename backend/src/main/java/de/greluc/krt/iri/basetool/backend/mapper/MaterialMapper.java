package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between Material entities and DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface MaterialMapper {
  /**
   * Maps a {@link Material} entity to its DTO. UEX-style {@code Integer} 0/1 flags ({@code
   * isIllegal}, {@code isVolatileQt}, {@code isVolatileTime}) are normalised to {@code Boolean} for
   * the client. {@code isManualEntry} is derived from {@code sourceSystems == MANUAL} (R9 Step 1):
   * the legacy {@code is_manual_entry} column is no longer read — the canonical provenance lives in
   * {@code source_systems} since the V116 backfill.
   */
  @Mapping(
      target = "isIllegal",
      expression = "java(entity.getIsIllegal() != null && entity.getIsIllegal() == 1)")
  @Mapping(
      target = "isVolatileQt",
      expression = "java(entity.getIsVolatileQt() != null && entity.getIsVolatileQt() == 1)")
  @Mapping(
      target = "isVolatileTime",
      expression = "java(entity.getIsVolatileTime() != null && entity.getIsVolatileTime() == 1)")
  @Mapping(
      target = "isManualEntry",
      expression =
          "java(entity.getSourceSystems()"
              + " == de.greluc.krt.iri.basetool.backend.model.MaterialSourceSystem.MANUAL)")
  MaterialDto toDto(Material entity);

  /**
   * Builds a new {@link Material} entity from the DTO. Boolean flags are converted back to
   * UEX-style {@code Integer} 0/1 storage. {@code isManualEntry} is intentionally not written back
   * (R9 Step 1): the legacy column is no longer a writer target — provenance is owned by {@code
   * sourceSystems}.
   */
  @Mapping(
      target = "isIllegal",
      expression = "java(dto.isIllegal() != null && dto.isIllegal() ? 1 : 0)")
  @Mapping(
      target = "isVolatileQt",
      expression = "java(dto.isVolatileQt() != null && dto.isVolatileQt() ? 1 : 0)")
  @Mapping(
      target = "isVolatileTime",
      expression = "java(dto.isVolatileTime() != null && dto.isVolatileTime() ? 1 : 0)")
  @Mapping(target = "isManualEntry", ignore = true)
  Material toEntity(MaterialDto dto);

  /**
   * Strips server-managed fields and body-supplied foreign-key references from a freshly mapped
   * entity for the POST/create flow, so a client cannot pre-set them (mass-assignment /
   * over-posting). {@code id} stays null so JPA performs an INSERT instead of a merge against an
   * existing row; {@code version} is left to the persistence provider; {@code refinedMaterial} and
   * {@code category} are not accepted through the request body here. A future create flow that
   * needs them should look the ids up via the service layer.
   *
   * <p>Declared as a static helper rather than a default mapping method so MapStruct does not
   * consider it a candidate for nested {@code MaterialDto -> Material} mappings inside other
   * mappers.
   */
  static Material stripServerManaged(Material entity) {
    if (entity != null) {
      entity.setId(null);
      entity.setVersion(null);
      entity.setRefinedMaterial(null);
      entity.setCategory(null);
    }
    return entity;
  }

  /** MapStruct default - converts a UEX-style 0/1 {@code Integer} to a nullable {@code Boolean}. */
  default Boolean mapIsIllegal(Integer value) {
    return value != null && value == 1;
  }

  /** MapStruct default - converts a {@code Boolean} back to a UEX-style 0/1 {@code Integer}. */
  default Integer mapIsIllegal(Boolean value) {
    return value != null && value ? 1 : 0;
  }
}
