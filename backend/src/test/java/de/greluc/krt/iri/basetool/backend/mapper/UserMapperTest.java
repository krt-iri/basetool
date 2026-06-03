package de.greluc.krt.iri.basetool.backend.mapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

class UserMapperTest {

  private UserMapper mapper;
  private OrgUnitMembershipRepository membershipRepository;
  private SquadronRepository squadronRepository;

  @BeforeEach
  void setUp() {
    // Post-R9 D3 (V101): the mapper derives squadron + flag fields from the membership table. Wire
    // the two repositories the abstract MapStruct mapper needs since we are not running inside a
    // Spring context.
    mapper = Mappers.getMapper(UserMapper.class);
    membershipRepository = mock(OrgUnitMembershipRepository.class);
    squadronRepository = mock(SquadronRepository.class);
    ReflectionTestUtils.setField(mapper, "membershipRepository", membershipRepository);
    ReflectionTestUtils.setField(mapper, "squadronRepository", squadronRepository);
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
    user.setEmail("jdoe@example.com");
    user.setRank(5);
    user.setDescription("desc");
    user.getRoles().add(admin);
    user.getRoles().add(officer);
    // No membership rows wired — the mapper projects squadron / isLogistician / isMissionManager
    // as null / false respectively for this fixture.
    when(membershipRepository.findAllByIdUserIdAndKind(user.getId(), OrgUnitKind.SQUADRON))
        .thenReturn(List.of());

    UserDto dto = mapper.toDto(user);
    assertNotNull(dto);
    assertEquals(user.getId(), dto.id());
    assertEquals("jdoe", dto.username());
    // PII: toDto deliberately omits email (it is re-added only on the /me self path via
    // UserController.withSelfEmail). It must be null on every projection this mapper produces.
    assertNull(dto.email());
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
    user.setEmail(null);
    user.setRank(null);
    user.setDescription(null);
    user.setRoles(null);
    when(membershipRepository.findAllByIdUserIdAndKind(user.getId(), OrgUnitKind.SQUADRON))
        .thenReturn(List.of());

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
