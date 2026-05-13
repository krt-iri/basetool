package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.RefiningMethod;
import de.greluc.krt.iri.basetool.backend.model.dto.RefiningMethodDto;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RefiningMethodMapperTest {

    private final RefiningMethodMapper mapper = Mappers.getMapper(RefiningMethodMapper.class);

    @Test
    void toDto_shouldMapAllFields() {
        // Given
        UUID id = UUID.randomUUID();
        RefiningMethod entity = new RefiningMethod();
        entity.setId(id);
        entity.setName("Cormack");
        entity.setDescription("High yield, slow");
        entity.setCode("CORMACK");
        entity.setRatingYield(95);
        entity.setRatingCost(70);
        entity.setRatingSpeed(30);

        // When
        RefiningMethodDto dto = mapper.toDto(entity);

        // Then
        assertNotNull(dto);
        assertEquals(id, dto.id());
        assertEquals("Cormack", dto.name());
        assertEquals("High yield, slow", dto.description());
        assertEquals("CORMACK", dto.code());
        assertEquals(95, dto.ratingYield());
        assertEquals(70, dto.ratingCost());
        assertEquals(30, dto.ratingSpeed());
    }

    @Test
    void toEntity_shouldMapAllFields() {
        // Given
        UUID id = UUID.randomUUID();
        RefiningMethodDto dto = new RefiningMethodDto(
                id, "Dinyx Solventation", "Balanced", "DINYX", 50, 60, 70
        );

        // When
        RefiningMethod entity = mapper.toEntity(dto);

        // Then
        assertNotNull(entity);
        assertEquals(id, entity.getId());
        assertEquals("Dinyx Solventation", entity.getName());
        assertEquals("Balanced", entity.getDescription());
        assertEquals("DINYX", entity.getCode());
        assertEquals(50, entity.getRatingYield());
        assertEquals(60, entity.getRatingCost());
        assertEquals(70, entity.getRatingSpeed());
    }

    @Test
    void roundtrip_shouldPreserveAllFields() {
        // Given
        RefiningMethodDto original = new RefiningMethodDto(
                UUID.randomUUID(), "Pyrometric Chromalysis", null, "PYRO", 40, 30, 100
        );

        // When
        RefiningMethodDto back = mapper.toDto(mapper.toEntity(original));

        // Then
        assertEquals(original, back);
    }

    @Test
    void nullSafety_shouldReturnNull_whenSourceNull() {
        assertNull(mapper.toDto(null));
        assertNull(mapper.toEntity(null));
    }
}
