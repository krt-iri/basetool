package de.greluc.krt.iri.basetool.backend.mapper;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.JobType;
import de.greluc.krt.iri.basetool.backend.model.JobTypeArchetype;
import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.JobTypeDto;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UserDto;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntityMappersTest {

  @Test
  void userToDto_shouldAggregateRoleNamesAndPermissions_andCopyScalars() {
    // Given
    Role admin = new Role();
    admin.setName("ADMIN");
    admin.setPermissions(new HashSet<>(Set.of("USER_MANAGE", "ROLE_ASSIGN")));

    Role officer = new Role();
    officer.setName("OFFICER");
    officer.setPermissions(new HashSet<>(Set.of("MISSION_MANAGE", "USER_MANAGE")));

    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setUsername("jdoe");
    user.setDisplayName("J. Doe");
    user.setFirstName("John");
    user.setLastName("Doe");
    user.setEmail("jdoe@example.com");
    user.setRank(5);
    user.setDescription("Pilot");
    user.setInKeycloak(true);
    user.setVersion(3L);
    user.setJoinDate(LocalDate.of(2024, 1, 15));
    user.setRoles(new HashSet<>(Set.of(admin, officer)));

    // When
    UserDto dto = EntityMappers.toDto(user);

    // Then
    assertNotNull(dto);
    assertEquals(userId, dto.id());
    assertEquals("jdoe", dto.username());
    assertEquals("J. Doe", dto.displayName());
    assertEquals("J. Doe", dto.effectiveName());
    assertEquals("John", dto.firstName());
    assertEquals("Doe", dto.lastName());
    assertEquals("jdoe@example.com", dto.email());
    assertEquals(5, dto.rank());
    assertEquals("Pilot", dto.description());
    assertEquals(Set.of("ADMIN", "OFFICER"), dto.roles());
    assertEquals(Set.of("USER_MANAGE", "ROLE_ASSIGN", "MISSION_MANAGE"), dto.permissions());
    // Post-R9 D3 (V101): the static EntityMappers helper does not read the (now-dropped)
    // legacy columns and projects the membership-derived fields as the default empty values —
    // see the class-level Javadoc. The Spring-managed UserMapper bean is the only path that
    // surfaces the real membership flag values.
    assertFalse(dto.isLogistician());
    assertFalse(dto.isMissionManager());
    assertNull(dto.squadron());
    assertTrue(dto.inKeycloak());
    assertEquals(3L, dto.version());
    assertEquals(LocalDate.of(2024, 1, 15), dto.joinDate());
  }

  @Test
  void userToDto_withoutDisplayName_shouldFallBackToUsername() {
    // Given
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername("solo");
    user.setDisplayName(null);
    user.setRoles(new HashSet<>());

    // When
    UserDto dto = EntityMappers.toDto(user);

    // Then
    assertEquals("solo", dto.effectiveName());
  }

  @Test
  void jobTypeToDto_shouldCopyParentIdWhenPresent() {
    // Given
    JobType parent = new JobType();
    parent.setId(UUID.randomUUID());

    UUID jtId = UUID.randomUUID();
    JobType jt = new JobType();
    jt.setId(jtId);
    jt.setName("Pilot");
    jt.setDescription("Flies");
    jt.setArchetype(JobTypeArchetype.CREW);
    jt.setParent(parent);
    jt.setActive(true);
    jt.setLeadershipRole(false);
    jt.setVersion(2L);

    // When
    JobTypeDto dto = EntityMappers.toDto(jt);

    // Then
    assertEquals(jtId, dto.id());
    assertEquals("Pilot", dto.name());
    assertEquals("Flies", dto.description());
    assertEquals(JobTypeArchetype.CREW, dto.archetype());
    assertEquals(parent.getId(), dto.parentId());
    assertTrue(dto.active());
    assertFalse(dto.isLeadershipRole());
    assertEquals(2L, dto.version());
  }

  @Test
  void jobTypeToDto_withoutParent_shouldHaveNullParentId() {
    // Given
    JobType jt = new JobType();
    jt.setId(UUID.randomUUID());
    jt.setName("Solo");
    jt.setArchetype(JobTypeArchetype.MISSION);

    // When
    JobTypeDto dto = EntityMappers.toDto(jt);

    // Then
    assertNull(dto.parentId());
  }

  @Test
  void jobTypeToEntity_shouldRebuildShallowParent_whenParentIdProvided() {
    // Given
    UUID parentId = UUID.randomUUID();
    UUID jtId = UUID.randomUUID();
    JobTypeDto dto =
        new JobTypeDto(jtId, "Child", "desc", JobTypeArchetype.CREW, parentId, true, false, 1L);

    // When
    JobType jt = EntityMappers.toEntity(dto);

    // Then
    assertNotNull(jt);
    assertEquals(jtId, jt.getId());
    assertEquals("Child", jt.getName());
    assertEquals("desc", jt.getDescription());
    assertEquals(JobTypeArchetype.CREW, jt.getArchetype());
    assertNotNull(jt.getParent());
    assertEquals(parentId, jt.getParent().getId());
  }

  @Test
  void jobTypeToEntity_withoutParentId_shouldHaveNullParent() {
    // Given
    JobTypeDto dto =
        new JobTypeDto(
            UUID.randomUUID(), "Solo", null, JobTypeArchetype.MISSION, null, true, true, 1L);

    // When
    JobType jt = EntityMappers.toEntity(dto);

    // Then
    assertNull(jt.getParent());
    assertTrue(jt.isLeadershipRole());
  }

  @Test
  void squadronToDto_shouldCopyAllFields() {
    // Given
    UUID id = UUID.randomUUID();
    Squadron s = new Squadron();
    s.setId(id);
    s.setName("Vanguard");
    s.setShorthand("VAN");
    s.setDescription("Test squad");
    s.setActive(true);
    s.setPromotionEnabled(false);
    s.setProfitEligible(true);
    s.setVersion(2L);

    // When
    SquadronDto dto = EntityMappers.toDto(s);

    // Then
    assertEquals(id, dto.id());
    assertEquals("Vanguard", dto.name());
    assertEquals("VAN", dto.shorthand());
    assertEquals("Test squad", dto.description());
    assertTrue(dto.active());
    // toDto must mirror the per-squadron promotion-feature flag — the admin
    // UI / sidebar visibility uses this value to decide whether the
    // promotion menu is exposed to a non-admin caller.
    assertFalse(dto.isPromotionEnabled());
    // toDto must also mirror the per-squadron profit-eligibility flag — the
    // admin settings page renders the responsible-picker opt-in from this value.
    assertTrue(dto.isProfitEligible());
    assertEquals(2L, dto.version());
  }

  @Test
  void squadronToEntity_shouldCopyScalars_butIgnoreActiveAndVersion() {
    // Given
    UUID id = UUID.randomUUID();
    SquadronDto dto = new SquadronDto(id, "Alpha", "ALP", "First squad", true, true, false, 5L);

    // When
    Squadron s = EntityMappers.toEntity(dto);

    // Then
    assertEquals(id, s.getId());
    assertEquals("Alpha", s.getName());
    assertEquals("ALP", s.getShorthand());
    assertEquals("First squad", s.getDescription());
    // toEntity intentionally omits active and version (managed elsewhere) —
    // entity defaults: active is true (initialised in the entity declaration),
    // version is null (set by JPA).
    assertTrue(s.isActive());
    assertNull(s.getVersion());
  }

  @Test
  void constructor_shouldBePrivateAndUtilityClassNotInstantiable() {
    // Ensures EntityMappers stays a static utility — guarding against accidental
    // refactors that try to inject it as a Spring bean.
    var ctors = EntityMappers.class.getDeclaredConstructors();
    assertEquals(1, ctors.length);
    assertFalse(ctors[0].canAccess(null));
  }
}
