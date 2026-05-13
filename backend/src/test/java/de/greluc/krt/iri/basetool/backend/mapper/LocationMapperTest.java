package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.dto.LocationDto;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocationMapperTest {

    private final LocationMapper mapper = Mappers.getMapper(LocationMapper.class);

    @Test
    void toDto_shouldMapExposedFields() {
        // Given
        UUID id = UUID.randomUUID();
        Location entity = new Location();
        entity.setId(id);
        entity.setName("Port Olisar");
        entity.setDescription("Crusader station");
        entity.setHidden(false);
        entity.setVersion(3L);

        // When
        LocationDto dto = mapper.toDto(entity);

        // Then
        assertNotNull(dto);
        assertEquals(id, dto.id());
        assertEquals("Port Olisar", dto.name());
        assertEquals("Crusader station", dto.description());
        assertFalse(dto.hidden());
        assertEquals(3L, dto.version());
    }

    @Test
    void toEntity_shouldMapExposedFields() {
        // Given
        UUID id = UUID.randomUUID();
        LocationDto dto = new LocationDto(id, "Lorville", "Hurston city", true, 1L);

        // When
        Location entity = mapper.toEntity(dto);

        // Then
        assertNotNull(entity);
        assertEquals(id, entity.getId());
        assertEquals("Lorville", entity.getName());
        assertEquals("Hurston city", entity.getDescription());
        assertTrue(entity.getHidden());
        assertEquals(1L, entity.getVersion());
        // Unmapped relations stay null (mapper ignores them)
        assertNull(entity.getCity());
        assertNull(entity.getSpaceStation());
    }

    @Test
    void stripServerManaged_shouldClearIdAndVersion() {
        // Given
        Location entity = new Location();
        entity.setId(UUID.randomUUID());
        entity.setVersion(5L);
        entity.setName("Untainted");

        // When
        Location stripped = LocationMapper.stripServerManaged(entity);

        // Then
        assertSame(entity, stripped, "stripServerManaged must mutate and return the same instance");
        assertNull(stripped.getId());
        assertNull(stripped.getVersion());
        // Other fields must remain untouched
        assertEquals("Untainted", stripped.getName());
    }

    @Test
    void stripServerManaged_shouldHandleNullEntity() {
        assertNull(LocationMapper.stripServerManaged(null));
    }

    @Test
    void nullSafety_shouldReturnNull_whenSourceNull() {
        assertNull(mapper.toDto(null));
        assertNull(mapper.toEntity(null));
    }
}
