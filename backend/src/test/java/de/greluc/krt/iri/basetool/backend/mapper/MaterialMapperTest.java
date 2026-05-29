package de.greluc.krt.iri.basetool.backend.mapper;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialCategory;
import de.greluc.krt.iri.basetool.backend.model.MaterialType;
import de.greluc.krt.iri.basetool.backend.model.QuantityType;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class MaterialMapperTest {

  private final MaterialMapper mapper = Mappers.getMapper(MaterialMapper.class);

  @Test
  void toDto_shouldMapScalarFieldsAndConvertIntegerFlagsToBoolean() {
    // Given
    UUID id = UUID.randomUUID();
    Material entity = new Material();
    entity.setId(id);
    entity.setName("Quantanium");
    entity.setType(MaterialType.RAW);
    entity.setQuantityType(QuantityType.SCU);
    entity.setDescription("Volatile mining commodity");
    entity.setIsIllegal(1); // 1 → true
    entity.setIsVolatileQt(1); // 1 → true
    entity.setIsVolatileTime(0); // 0 → false
    entity.setIsManualRawMaterial(true);
    entity.setIsJobOrder(false);
    entity.setIsVisible(false); // wiki-only invisible row → must surface on the DTO
    entity.setVersion(4L);

    // When
    MaterialDto dto = mapper.toDto(entity);

    // Then
    assertNotNull(dto);
    assertEquals(id, dto.id());
    assertEquals("Quantanium", dto.name());
    assertEquals("RAW", dto.type());
    assertEquals("SCU", dto.quantityType());
    assertEquals("Volatile mining commodity", dto.description());
    assertTrue(dto.isIllegal());
    assertTrue(dto.isVolatileQt());
    assertFalse(dto.isVolatileTime());
    assertTrue(dto.isManualRawMaterial());
    assertFalse(dto.isJobOrder());
    assertFalse(dto.isVisible(), "is_visible maps through to the DTO");
    assertEquals(4L, dto.version());
  }

  @Test
  void toDto_withNullFlagIntegers_shouldMapToFalse() {
    // Given
    Material entity = new Material();
    entity.setName("Iron");
    entity.setType(MaterialType.RAW);
    entity.setIsIllegal(null);
    entity.setIsVolatileQt(null);
    entity.setIsVolatileTime(null);

    // When
    MaterialDto dto = mapper.toDto(entity);

    // Then — the mapper expression treats null as 0/false
    assertFalse(dto.isIllegal());
    assertFalse(dto.isVolatileQt());
    assertFalse(dto.isVolatileTime());
  }

  @Test
  void toEntity_shouldConvertBooleanFlagsToInteger1or0() {
    // Given
    UUID id = UUID.randomUUID();
    MaterialDto dto =
        new MaterialDto(
            id,
            "Laranite",
            "RAW",
            "SCU",
            null,
            null,
            null,
            true,
            false,
            true,
            false,
            true,
            null,
            false,
            1L);

    // When
    Material entity = mapper.toEntity(dto);

    // Then
    assertNotNull(entity);
    assertEquals(id, entity.getId());
    assertEquals("Laranite", entity.getName());
    assertEquals(MaterialType.RAW, entity.getType());
    assertEquals(QuantityType.SCU, entity.getQuantityType());
    assertEquals(1, entity.getIsIllegal());
    assertEquals(0, entity.getIsVolatileQt());
    assertEquals(1, entity.getIsVolatileTime());
    assertEquals(Boolean.FALSE, entity.getIsManualRawMaterial());
    assertEquals(Boolean.TRUE, entity.getIsJobOrder());
    assertEquals(Boolean.FALSE, entity.getIsVisible(), "is_visible maps back to the entity");
    assertEquals(1L, entity.getVersion());
  }

  @Test
  void toEntity_withNullBooleanFlags_shouldMapToInteger0() {
    // Given
    MaterialDto dto =
        new MaterialDto(
            UUID.randomUUID(),
            "Tin",
            "RAW",
            "SCU",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            1L);

    // When
    Material entity = mapper.toEntity(dto);

    // Then — null Boolean → 0
    assertEquals(0, entity.getIsIllegal());
    assertEquals(0, entity.getIsVolatileQt());
    assertEquals(0, entity.getIsVolatileTime());
  }

  @Test
  void mapIsIllegal_integerToBoolean() {
    assertTrue(mapper.mapIsIllegal(1));
    assertFalse(mapper.mapIsIllegal(0));
    assertFalse(mapper.mapIsIllegal((Integer) null));
    assertFalse(mapper.mapIsIllegal(42), "Only 1 maps to true");
  }

  @Test
  void mapIsIllegal_booleanToInteger() {
    assertEquals(1, mapper.mapIsIllegal(Boolean.TRUE));
    assertEquals(0, mapper.mapIsIllegal(Boolean.FALSE));
    assertEquals(0, mapper.mapIsIllegal((Boolean) null));
  }

  @Test
  void stripServerManaged_shouldClearIdVersionAndForeignKeyRefs() {
    // Given
    Material entity = new Material();
    entity.setId(UUID.randomUUID());
    entity.setVersion(5L);
    entity.setName("Untainted");
    entity.setRefinedMaterial(new Material());
    entity.setCategory(new MaterialCategory());

    // When
    Material stripped = MaterialMapper.stripServerManaged(entity);

    // Then
    assertSame(entity, stripped);
    assertNull(stripped.getId());
    assertNull(stripped.getVersion());
    assertNull(stripped.getRefinedMaterial());
    assertNull(stripped.getCategory());
    assertEquals("Untainted", stripped.getName());
  }

  @Test
  void stripServerManaged_shouldHandleNullEntity() {
    assertNull(MaterialMapper.stripServerManaged(null));
  }

  @Test
  void nullSafety_shouldReturnNull_whenSourceNull() {
    assertNull(mapper.toDto(null));
    assertNull(mapper.toEntity(null));
  }
}
