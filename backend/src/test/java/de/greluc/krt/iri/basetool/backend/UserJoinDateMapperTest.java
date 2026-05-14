package de.greluc.krt.iri.basetool.backend;

import static org.assertj.core.api.Assertions.assertThat;

import de.greluc.krt.iri.basetool.backend.mapper.UserMapper;
import de.greluc.krt.iri.basetool.backend.mapper.UserMapperImpl;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.UserDto;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserJoinDateMapperTest {

  private final UserMapper userMapper = new UserMapperImpl();

  @Test
  void shouldMapJoinDate_WhenSet() {
    // Given
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername("mapper_test");
    user.setRank(1);
    LocalDate joinDate = LocalDate.of(2023, 7, 4);
    user.setJoinDate(joinDate);

    // When
    UserDto dto = userMapper.toDto(user);

    // Then
    assertThat(dto.joinDate()).isEqualTo(joinDate);
  }

  @Test
  void shouldMapJoinDate_AsNull_WhenNotSet() {
    // Given
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername("mapper_test_null");
    user.setRank(1);
    user.setJoinDate(null);

    // When
    UserDto dto = userMapper.toDto(user);

    // Then
    assertThat(dto.joinDate()).isNull();
  }
}
