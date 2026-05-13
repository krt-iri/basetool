package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.StarSystem;
import de.greluc.krt.iri.basetool.backend.model.dto.StarSystemDto;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StarSystemMapperTest {

    private final StarSystemMapper mapper = Mappers.getMapper(StarSystemMapper.class);

    @Test
    void toDto_shouldMapAllFields() {
        // Given
        UUID id = UUID.randomUUID();
        StarSystem entity = new StarSystem();
        entity.setId(id);
        entity.setIdSystem(42);
        entity.setName("Stanton");
        entity.setDescription("UEE system");
        entity.setIsAvailableLive(true);
        entity.setWiki("https://example.org/stanton");
        entity.setJurisdictionName("UEE");
        entity.setFactionName("Empire");
        entity.setVersion(3L);

        // When
        StarSystemDto dto = mapper.toDto(entity);

        // Then
        assertNotNull(dto);
        assertEquals(id, dto.id());
        assertEquals(42, dto.idSystem());
        assertEquals("Stanton", dto.name());
        assertEquals("UEE system", dto.description());
        assertTrue(dto.isAvailableLive());
        assertEquals("https://example.org/stanton", dto.wiki());
        assertEquals("UEE", dto.jurisdictionName());
        assertEquals("Empire", dto.factionName());
        assertEquals(3L, dto.version());
    }

    @Test
    void toEntity_shouldMapAllFields() {
        // Given
        UUID id = UUID.randomUUID();
        StarSystemDto dto = new StarSystemDto(
                id, 99, "Pyro", "Lawless", false,
                "https://example.org/pyro", "Lawless", "Pirates", 1L
        );

        // When
        StarSystem entity = mapper.toEntity(dto);

        // Then
        assertNotNull(entity);
        assertEquals(id, entity.getId());
        assertEquals(99, entity.getIdSystem());
        assertEquals("Pyro", entity.getName());
        assertEquals("Lawless", entity.getDescription());
        assertEquals(false, entity.getIsAvailableLive());
        assertEquals("https://example.org/pyro", entity.getWiki());
        assertEquals("Lawless", entity.getJurisdictionName());
        assertEquals("Pirates", entity.getFactionName());
        assertEquals(1L, entity.getVersion());
    }

    @Test
    void roundtrip_shouldPreserveAllFields() {
        // Given
        StarSystemDto dto = new StarSystemDto(
                UUID.randomUUID(), 1, "Sol", "Cradle of humanity",
                true, "wiki", "UEE", "Empire", 5L
        );

        // When
        StarSystem entity = mapper.toEntity(dto);
        StarSystemDto back = mapper.toDto(entity);

        // Then
        assertEquals(dto, back);
    }

    @Test
    void nullSafety_shouldReturnNull_whenSourceNull() {
        assertNull(mapper.toDto(null));
        assertNull(mapper.toEntity(null));
    }
}
