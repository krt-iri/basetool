package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.OperationStatus;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationUpdateDto;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OperationMapperTest {

    private final OperationMapper mapper = Mappers.getMapper(OperationMapper.class);

    @Test
    void toDto_shouldMapAllFields() {
        // Given
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-01-01T10:00:00Z");
        Instant updated = Instant.parse("2026-02-01T10:00:00Z");
        Operation entity = new Operation();
        entity.setId(id);
        entity.setName("Operation Sunfire");
        entity.setDescription("Strike package");
        entity.setStatus(OperationStatus.ACTIVE);
        entity.setVersion(5L);
        entity.setCreatedAt(created);
        entity.setUpdatedAt(updated);

        // When
        OperationDto dto = mapper.toDto(entity);

        // Then
        assertNotNull(dto);
        assertEquals(id, dto.id());
        assertEquals("Operation Sunfire", dto.name());
        assertEquals("Strike package", dto.description());
        assertEquals(OperationStatus.ACTIVE, dto.status());
        assertEquals(5L, dto.version());
        assertEquals(created, dto.createdAt());
        assertEquals(updated, dto.updatedAt());
    }

    @Test
    void toEntity_fromCreateDto_shouldIgnoreIdAndTimestampsAndMissions() {
        // Given
        OperationCreateDto create = new OperationCreateDto("Op Aurora", "Recon", OperationStatus.PLANNED);

        // When
        Operation entity = mapper.toEntity(create);

        // Then
        assertNotNull(entity);
        assertEquals("Op Aurora", entity.getName());
        assertEquals("Recon", entity.getDescription());
        assertEquals(OperationStatus.PLANNED, entity.getStatus());
        // Explicitly ignored — must not leak from a body-supplied DTO
        assertNull(entity.getId());
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());
        assertNotNull(entity.getMissions(), "missions collection should be initialised by entity defaults");
        assertTrue(entity.getMissions().isEmpty());
    }

    @Test
    void updateEntity_shouldOverwriteScalarFieldsButNotIdTimestampsOrMissions() {
        // Given an existing managed entity
        UUID existingId = UUID.randomUUID();
        Instant originalCreated = Instant.parse("2026-01-01T10:00:00Z");
        Instant originalUpdated = Instant.parse("2026-02-01T10:00:00Z");

        Operation entity = new Operation();
        entity.setId(existingId);
        entity.setName("Old name");
        entity.setDescription("Old desc");
        entity.setStatus(OperationStatus.PLANNED);
        entity.setCreatedAt(originalCreated);
        entity.setUpdatedAt(originalUpdated);

        OperationUpdateDto update = new OperationUpdateDto(
                "New name", "New desc", OperationStatus.COMPLETED, 9L
        );

        // When
        mapper.updateEntity(update, entity);

        // Then — scalar fields overwritten
        assertEquals("New name", entity.getName());
        assertEquals("New desc", entity.getDescription());
        assertEquals(OperationStatus.COMPLETED, entity.getStatus());
        // Id and audit timestamps explicitly ignored (server-managed)
        assertEquals(existingId, entity.getId());
        assertEquals(originalCreated, entity.getCreatedAt());
        assertEquals(originalUpdated, entity.getUpdatedAt());
        // Missions collection ignored — keeps reference
        assertNotNull(entity.getMissions());
    }

    @Test
    void nullSafety_shouldReturnNull_whenSourceNull() {
        assertNull(mapper.toDto(null));
        assertNull(mapper.toEntity(null));
    }
}
