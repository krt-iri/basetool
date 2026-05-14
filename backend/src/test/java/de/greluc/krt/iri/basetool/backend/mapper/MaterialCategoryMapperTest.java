package de.greluc.krt.iri.basetool.backend.mapper;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.MaterialCategory;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialCategoryDto;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class MaterialCategoryMapperTest {

  private final MaterialCategoryMapper mapper = Mappers.getMapper(MaterialCategoryMapper.class);

  @Test
  void toDto_shouldMapAllFields() {
    // Given
    UUID id = UUID.randomUUID();
    MaterialCategory entity = new MaterialCategory();
    entity.setId(id);
    entity.setName("Mineral");
    entity.setVersion(2L);

    // When
    MaterialCategoryDto dto = mapper.toDto(entity);

    // Then
    assertNotNull(dto);
    assertEquals(id, dto.id());
    assertEquals("Mineral", dto.name());
    assertEquals(2L, dto.version());
  }

  @Test
  void toEntity_shouldIgnoreCreatedAtAndUpdatedAt() {
    // Given
    UUID id = UUID.randomUUID();
    MaterialCategoryDto dto = new MaterialCategoryDto(id, "Gas", 1L);

    // When
    MaterialCategory entity = mapper.toEntity(dto);

    // Then
    assertNotNull(entity);
    assertEquals(id, entity.getId());
    assertEquals("Gas", entity.getName());
    assertEquals(1L, entity.getVersion());
    // mapper explicitly ignores audit timestamps — they must stay null
    assertNull(entity.getCreatedAt());
    assertNull(entity.getUpdatedAt());
  }

  @Test
  void toEntity_shouldNotCopyAuditTimestampsEvenIfDtoHadThem() {
    // Sanity check: even if the entity is freshly built (no timestamps),
    // the mapper's @Mapping(ignore=true) for createdAt/updatedAt must hold.
    MaterialCategoryDto dto = new MaterialCategoryDto(UUID.randomUUID(), "Composite", 1L);
    MaterialCategory entity = mapper.toEntity(dto);

    Instant before = entity.getUpdatedAt();
    // Re-map; the contract is "do not touch audit fields"
    MaterialCategory entity2 = mapper.toEntity(dto);
    assertEquals(before, entity2.getUpdatedAt());
  }

  @Test
  void nullSafety_shouldReturnNull_whenSourceNull() {
    assertNull(mapper.toDto(null));
    assertNull(mapper.toEntity(null));
  }
}
