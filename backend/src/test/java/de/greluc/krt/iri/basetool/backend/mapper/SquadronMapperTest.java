package de.greluc.krt.iri.basetool.backend.mapper;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class SquadronMapperTest {

  private final SquadronMapper mapper = Mappers.getMapper(SquadronMapper.class);

  @Test
  void toDto_and_back_shouldPreserveFields() {
    Squadron s = new Squadron();
    s.setId(UUID.randomUUID());
    s.setName("Vanguard");
    s.setShorthand("VAN");
    s.setDescription("Test squad");

    SquadronDto dto = mapper.toDto(s);
    assertNotNull(dto);
    assertEquals(s.getId(), dto.id());
    assertEquals("Vanguard", dto.name());
    assertEquals("VAN", dto.shorthand());
    assertEquals("Test squad", dto.description());

    Squadron back = mapper.toEntity(dto);
    assertEquals(dto.id(), back.getId());
    assertEquals(dto.name(), back.getName());
    assertEquals(dto.shorthand(), back.getShorthand());
    assertEquals(dto.description(), back.getDescription());
  }

  @Test
  void nullSafety_shouldReturnNull_whenSourceNull() {
    assertNull(mapper.toDto(null));
    assertNull(mapper.toEntity(null));
  }
}
