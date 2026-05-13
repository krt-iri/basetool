package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.MaterialCategory;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialCategoryDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between Material Category entities and DTOs. */
@Mapper(componentModel = "spring")
public interface MaterialCategoryMapper {
  /** Maps a {@link MaterialCategory} entity to its outbound DTO. */
  MaterialCategoryDto toDto(MaterialCategory entity);

  /**
   * Builds a new {@link MaterialCategory} entity from the inbound DTO. Timestamps are owned by the
   * persistence provider and ignored.
   */
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  MaterialCategory toEntity(MaterialCategoryDto dto);
}
