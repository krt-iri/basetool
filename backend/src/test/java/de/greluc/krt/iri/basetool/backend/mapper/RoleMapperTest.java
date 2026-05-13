package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.dto.RoleDto;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoleMapperTest {

    private final RoleMapper mapper = Mappers.getMapper(RoleMapper.class);

    @Test
    void toDto_shouldMapBasicFieldsAndPermissions() {
        // Given
        Role role = new Role();
        role.setId(7L);
        role.setName("ADMIN");
        role.setDescription("Full access");
        role.setPermissions(new HashSet<>(Set.of("USER_MANAGE", "ROLE_ASSIGN")));
        role.setVersion(2L);

        // When
        RoleDto dto = mapper.toDto(role);

        // Then
        assertNotNull(dto);
        assertEquals(7L, dto.id());
        assertEquals("ADMIN", dto.name());
        assertEquals("Full access", dto.description());
        assertEquals(Set.of("USER_MANAGE", "ROLE_ASSIGN"), dto.permissions());
        assertEquals(2L, dto.version());
    }

    @Test
    void toEntity_shouldMapBasicFieldsAndPermissions() {
        // Given
        RoleDto dto = new RoleDto(
                3L, "OFFICER", "Squadron officer",
                Set.of("MISSION_MANAGE", "USER_MANAGE"), 1L
        );

        // When
        Role role = mapper.toEntity(dto);

        // Then
        assertNotNull(role);
        assertEquals(3L, role.getId());
        assertEquals("OFFICER", role.getName());
        assertEquals("Squadron officer", role.getDescription());
        assertEquals(Set.of("MISSION_MANAGE", "USER_MANAGE"), role.getPermissions());
        assertEquals(1L, role.getVersion());
    }

    @Test
    void toDto_withEmptyPermissions_shouldProduceEmptySet() {
        // Given
        Role role = new Role();
        role.setId(1L);
        role.setName("GUEST");
        role.setPermissions(new HashSet<>());

        // When
        RoleDto dto = mapper.toDto(role);

        // Then
        assertNotNull(dto.permissions());
        assertTrue(dto.permissions().isEmpty());
    }

    @Test
    void nullSafety_shouldReturnNull_whenSourceNull() {
        assertNull(mapper.toDto(null));
        assertNull(mapper.toEntity(null));
    }
}
