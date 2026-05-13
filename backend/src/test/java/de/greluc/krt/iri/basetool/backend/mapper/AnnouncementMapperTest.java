package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Announcement;
import de.greluc.krt.iri.basetool.backend.model.dto.AnnouncementDto;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnnouncementMapperTest {

    private final AnnouncementMapper mapper = Mappers.getMapper(AnnouncementMapper.class);

    @Test
    void toDto_shouldMapAllFields() {
        // Given
        UUID id = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-05-13T10:15:30Z");
        Announcement entity = new Announcement();
        entity.setId(id);
        entity.setContent("Server maintenance tonight");
        entity.setUpdatedAt(updatedAt);
        entity.setVersion(7L);

        // When
        AnnouncementDto dto = mapper.toDto(entity);

        // Then
        assertNotNull(dto);
        assertEquals(id, dto.id());
        assertEquals("Server maintenance tonight", dto.content());
        assertEquals(updatedAt, dto.updatedAt());
        assertEquals(7L, dto.version());
    }

    @Test
    void toEntity_shouldMapAllFields() {
        // Given
        UUID id = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-04-01T00:00:00Z");
        AnnouncementDto dto = new AnnouncementDto(id, "Welcome", updatedAt, 1L);

        // When
        Announcement entity = mapper.toEntity(dto);

        // Then
        assertNotNull(entity);
        assertEquals(id, entity.getId());
        assertEquals("Welcome", entity.getContent());
        assertEquals(updatedAt, entity.getUpdatedAt());
        assertEquals(1L, entity.getVersion());
    }

    @Test
    void toDto_withNullFields_shouldPassThroughNulls() {
        // Given
        Announcement entity = new Announcement();
        // id, content, updatedAt all null

        // When
        AnnouncementDto dto = mapper.toDto(entity);

        // Then
        assertNotNull(dto);
        assertNull(dto.id());
        assertNull(dto.content());
        assertNull(dto.updatedAt());
        assertNull(dto.version());
    }

    @Test
    void nullSafety_shouldReturnNull_whenSourceNull() {
        assertNull(mapper.toDto(null));
        assertNull(mapper.toEntity(null));
    }
}
