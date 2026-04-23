package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Ensures that {@link MissionMapper#toReferenceDto(Mission)} exposes
 * {@code plannedStartTime} as UTC {@link Instant}, so dropdowns can render
 * "Name - Date" (locale-formatted, date-only) on the view layer.
 */
class MissionReferenceDtoMappingTest {

    private final MissionMapper mapper = new MissionMapperImpl();

    @Test
    void shouldMapPlannedStartTimeToReferenceDto() {
        // Given
        Mission mission = new Mission();
        mission.setId(UUID.randomUUID());
        mission.setName("Operation Orange Sun");
        Instant utc = Instant.parse("2026-05-12T09:30:00Z");
        mission.setPlannedStartTime(utc);

        // When
        MissionReferenceDto dto = mapper.toReferenceDto(mission);

        // Then
        assertNotNull(dto);
        assertEquals("Operation Orange Sun", dto.name());
        assertEquals(utc, dto.plannedStartTime(), "plannedStartTime must be preserved as UTC Instant");
    }

    @Test
    void shouldAllowNullPlannedStartTime() {
        Mission mission = new Mission();
        mission.setId(UUID.randomUUID());
        mission.setName("Undated Mission");

        MissionReferenceDto dto = mapper.toReferenceDto(mission);

        assertNotNull(dto);
        assertEquals("Undated Mission", dto.name());
        assertNull(dto.plannedStartTime());
    }
}
