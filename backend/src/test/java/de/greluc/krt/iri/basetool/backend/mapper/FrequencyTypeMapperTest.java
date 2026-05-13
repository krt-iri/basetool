package de.greluc.krt.iri.basetool.backend.mapper;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.FrequencyType;
import de.greluc.krt.iri.basetool.backend.model.dto.FrequencyTypeDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class FrequencyTypeMapperTest {

  private final FrequencyTypeMapper mapper = Mappers.getMapper(FrequencyTypeMapper.class);

  @Test
  void toDto_shouldMapAllFields() {
    // Given
    UUID id = UUID.randomUUID();
    FrequencyType entity = new FrequencyType();
    entity.setId(id);
    entity.setName("Combat");
    entity.setDescription("Combat radio frequency");
    entity.setActive(true);
    entity.setSortIndex(5);
    entity.setVersion(4L);

    // When
    FrequencyTypeDto dto = mapper.toDto(entity);

    // Then
    assertNotNull(dto);
    assertEquals(id, dto.id());
    assertEquals("Combat", dto.name());
    assertEquals("Combat radio frequency", dto.description());
    assertTrue(dto.active());
    assertEquals(5, dto.sortIndex());
    assertEquals(4L, dto.version());
  }

  @Test
  void toEntity_shouldMapAllFields() {
    // Given
    UUID id = UUID.randomUUID();
    FrequencyTypeDto dto = new FrequencyTypeDto(id, "Recon", "Recon channel", false, 12, 2L);

    // When
    FrequencyType entity = mapper.toEntity(dto);

    // Then
    assertNotNull(entity);
    assertEquals(id, entity.getId());
    assertEquals("Recon", entity.getName());
    assertEquals("Recon channel", entity.getDescription());
    assertFalse(entity.isActive());
    assertEquals(12, entity.getSortIndex());
    assertEquals(2L, entity.getVersion());
  }

  @Test
  void roundtrip_shouldPreserveAllFields() {
    // Given
    FrequencyTypeDto original =
        new FrequencyTypeDto(
            UUID.randomUUID(), "Logistics", "Cargo and supply chatter", true, 1, 7L);

    // When
    FrequencyTypeDto back = mapper.toDto(mapper.toEntity(original));

    // Then
    assertEquals(original, back);
  }

  @Test
  void nullSafety_shouldReturnNull_whenSourceNull() {
    assertNull(mapper.toDto(null));
    assertNull(mapper.toEntity(null));
  }
}
