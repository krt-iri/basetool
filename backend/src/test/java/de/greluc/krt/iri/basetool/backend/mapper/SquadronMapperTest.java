package de.greluc.krt.iri.basetool.backend.mapper;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.SpecialCommand;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronDto;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronReferenceDto;
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

  @Test
  void orgUnitToReferenceDto_shouldProjectSquadronOwner() {
    // Given a Squadron-typed owner
    Squadron squadron = new Squadron();
    squadron.setId(UUID.randomUUID());
    squadron.setName("IRIDIUM");
    squadron.setShorthand("IRI");

    // When projected to the slim owner reference
    SquadronReferenceDto ref = mapper.orgUnitToReferenceDto(squadron);

    // Then the id / name / shorthand triplet is carried over
    assertNotNull(ref);
    assertEquals(squadron.getId(), ref.id());
    assertEquals("IRIDIUM", ref.name());
    assertEquals("IRI", ref.shorthand());
  }

  @Test
  void orgUnitToReferenceDto_shouldProjectSpecialCommandOwner() {
    // Given a Spezialkommando-typed owner (the case that previously surfaced as null)
    SpecialCommand sk = new SpecialCommand();
    sk.setId(UUID.randomUUID());
    sk.setName("Special Command Alpha");
    sk.setShorthand("SKA");

    // When projected to the slim owner reference
    SquadronReferenceDto ref = mapper.orgUnitToReferenceDto(sk);

    // Then the SK surfaces its own id / name / shorthand instead of null
    assertNotNull(ref);
    assertEquals(sk.getId(), ref.id());
    assertEquals("Special Command Alpha", ref.name());
    assertEquals("SKA", ref.shorthand());
  }

  @Test
  void orgUnitToReferenceDto_shouldReturnNull_whenOrgUnitNull() {
    assertNull(mapper.orgUnitToReferenceDto(null));
  }
}
