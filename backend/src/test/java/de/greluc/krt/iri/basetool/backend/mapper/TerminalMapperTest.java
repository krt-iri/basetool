package de.greluc.krt.iri.basetool.backend.mapper;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.Terminal;
import de.greluc.krt.iri.basetool.backend.model.dto.TerminalDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class TerminalMapperTest {

  private final TerminalMapper mapper = Mappers.getMapper(TerminalMapper.class);

  @Test
  void toDto_shouldMapExposedFields() {
    // Given — only the fields that TerminalDto actually exposes.
    UUID id = UUID.randomUUID();
    java.time.Instant syncedAt = java.time.Instant.parse("2026-05-16T12:34:56Z");
    Terminal entity = new Terminal();
    entity.setId(id);
    entity.setName("Area 18 Trade & Development Division");
    entity.setNickname("TDD A18");
    entity.setStarSystemName("Stanton");
    entity.setPlanetName("ArcCorp");
    entity.setCityName("Area18");
    entity.setSpaceStationName(null);
    entity.setHasLoadingDock(true);
    entity.setIsAutoLoad(false);
    entity.setHasLoadingDockOverridden(true);
    entity.setIsAutoLoadOverridden(false);
    entity.setUexHasLoadingDock(false);
    entity.setUexIsAutoLoad(true);
    entity.setUexSyncedAt(syncedAt);
    entity.setHidden(true);

    // When
    TerminalDto dto = mapper.toDto(entity);

    // Then
    assertNotNull(dto);
    assertEquals(id, dto.id());
    assertEquals("Area 18 Trade & Development Division", dto.name());
    assertEquals("TDD A18", dto.nickname());
    assertEquals("Stanton", dto.starSystemName());
    assertEquals("ArcCorp", dto.planetName());
    assertEquals("Area18", dto.cityName());
    assertNull(dto.spaceStationName());
    assertTrue(dto.hasLoadingDock());
    assertFalse(dto.isAutoLoad());
    assertTrue(dto.hasLoadingDockOverridden());
    assertFalse(dto.isAutoLoadOverridden());
    // The raw UEX mirror columns are exposed independently of the override flags so
    // the admin UI can show what UEX currently claims even while a pin is active.
    assertFalse(dto.uexHasLoadingDock());
    assertTrue(dto.uexIsAutoLoad());
    assertEquals(syncedAt, dto.uexSyncedAt());
    assertTrue(dto.hidden());
  }

  @Test
  void toEntity_shouldMapDtoFields_andLeaveUnmappedFieldsAtDefaults() {
    // Given
    UUID id = UUID.randomUUID();
    java.time.Instant syncedAt = java.time.Instant.parse("2026-05-16T12:34:56Z");
    TerminalDto dto =
        new TerminalDto(
            id,
            "Lorville TDD",
            "TDD LV",
            "Stanton",
            "Hurston",
            "Lorville",
            null,
            true,
            false,
            true,
            false,
            false,
            true,
            syncedAt,
            false);

    // When
    Terminal entity = mapper.toEntity(dto);

    // Then
    assertNotNull(entity);
    assertEquals(id, entity.getId());
    assertEquals("Lorville TDD", entity.getName());
    assertEquals("TDD LV", entity.getNickname());
    assertEquals("Stanton", entity.getStarSystemName());
    assertEquals("Hurston", entity.getPlanetName());
    assertEquals("Lorville", entity.getCityName());
    assertNull(entity.getSpaceStationName());
    assertTrue(entity.getHasLoadingDock());
    assertFalse(entity.getIsAutoLoad());
    assertTrue(entity.getHasLoadingDockOverridden());
    assertFalse(entity.getIsAutoLoadOverridden());
    assertFalse(entity.getUexHasLoadingDock());
    assertTrue(entity.getUexIsAutoLoad());
    assertEquals(syncedAt, entity.getUexSyncedAt());
    assertFalse(entity.getHidden());
    // Fields not present in DTO must stay at entity defaults
    assertNull(entity.getIdTerminal());
    assertNull(entity.getCode());
    assertNull(entity.getOrbitName());
    assertNull(entity.getMoonName());
    assertNull(entity.getOutpostName());
    assertNull(entity.getFactionName());
    assertNull(entity.getCompanyName());
  }

  @Test
  void nullSafety_shouldReturnNull_whenSourceNull() {
    assertNull(mapper.toDto(null));
    assertNull(mapper.toEntity(null));
  }
}
