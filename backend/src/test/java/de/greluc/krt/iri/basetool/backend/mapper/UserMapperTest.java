package de.greluc.krt.iri.basetool.backend.mapper;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.UserDto;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

class UserMapperTest {

  private UserMapper mapper;

  @BeforeEach
  void setUp() {
    // UserMapperImpl @Autowires SquadronMapper for the new `squadron` projection — wire it
    // manually since we are not running inside a Spring context.
    mapper = Mappers.getMapper(UserMapper.class);
    ReflectionTestUtils.setField(mapper, "squadronMapper", Mappers.getMapper(SquadronMapper.class));
  }

  @Test
  void toDto_shouldMapBasicFieldsAndAggregates() {
    Role admin = new Role();
    admin.setName("ADMIN");
    admin.setPermissions(new HashSet<>(Set.of("USER_MANAGE", "ROLE_ASSIGN")));

    Role officer = new Role();
    officer.setName("OFFICER");
    officer.setPermissions(new HashSet<>(Set.of("MISSION_MANAGE", "USER_MANAGE")));

    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername("jdoe");
    user.setFirstName("John");
    user.setLastName("Doe");
    user.setEmail("jdoe@example.com");
    user.setRank(5);
    user.setDescription("desc");
    user.getRoles().add(admin);
    user.getRoles().add(officer);

    UserDto dto = mapper.toDto(user);
    assertNotNull(dto);
    assertEquals(user.getId(), dto.id());
    assertEquals("jdoe", dto.username());
    assertEquals("John", dto.firstName());
    assertEquals("Doe", dto.lastName());
    assertEquals("jdoe@example.com", dto.email());
    assertEquals(5, dto.rank());
    assertEquals("desc", dto.description());
    assertEquals(Set.of("ADMIN", "OFFICER"), dto.roles());
    assertEquals(Set.of("USER_MANAGE", "ROLE_ASSIGN", "MISSION_MANAGE"), dto.permissions());
  }

  @Test
  void toDto_withNullRoles_shouldReturnEmptySets() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername("empty");
    user.setFirstName(null);
    user.setLastName(null);
    user.setEmail(null);
    user.setRank(null);
    user.setDescription(null);
    user.setRoles(null);

    UserDto dto = mapper.toDto(user);
    assertNotNull(dto);
    assertNotNull(dto.roles());
    assertTrue(dto.roles().isEmpty());
    assertNotNull(dto.permissions());
    assertTrue(dto.permissions().isEmpty());
  }

  @Test
  void nullSafety_shouldReturnNull_whenSourceNull() {
    assertNull(mapper.toDto(null));
  }
}
