package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.SystemSetting;
import de.greluc.krt.iri.basetool.backend.model.dto.SystemSettingDto;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.*;

class SystemSettingMapperTest {

    private final SystemSettingMapper mapper = Mappers.getMapper(SystemSettingMapper.class);

    @Test
    void toDto_shouldMapKeyValueAndVersion() {
        // Given
        SystemSetting setting = SystemSetting.builder()
                .id("feature.flag.combat")
                .value("ENABLED")
                .build();
        setting.setVersion(3L);

        // When
        SystemSettingDto dto = mapper.toDto(setting);

        // Then
        assertNotNull(dto);
        assertEquals("feature.flag.combat", dto.id());
        assertEquals("ENABLED", dto.value());
        assertEquals(3L, dto.version());
    }

    @Test
    void toDto_withNullSource_shouldReturnNull() {
        assertNull(mapper.toDto(null));
    }
}
