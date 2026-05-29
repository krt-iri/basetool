package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.MaterialExternalAlias;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialExternalAliasDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between {@link MaterialExternalAlias} entities and the {@link
 * MaterialExternalAliasDto} response shape. Write payloads use the dedicated {@code …Request}
 * records and are handled directly by {@code MaterialExternalAliasService} (no MapStruct entity
 * construction).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface MaterialExternalAliasMapper {

  /**
   * Maps an entity to its DTO. Denormalises {@code material.id} / {@code material.name} into flat
   * fields so the admin table view does not need to traverse the lazy {@code Material} association
   * after the response leaves the transaction boundary.
   *
   * @param entity persistent alias row
   * @return DTO suitable for direct JSON serialisation
   */
  @Mapping(target = "materialId", source = "material.id")
  @Mapping(target = "materialName", source = "material.name")
  @Mapping(target = "sourceSystem", expression = "java(entity.getSourceSystem().name())")
  MaterialExternalAliasDto toDto(MaterialExternalAlias entity);
}
