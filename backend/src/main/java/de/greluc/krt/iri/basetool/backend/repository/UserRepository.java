package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto(u.id, u.username, u.displayName, CASE WHEN (u.displayName IS NOT NULL AND u.displayName <> '') THEN u.displayName ELSE u.username END, u.rank) FROM User u ORDER BY u.displayName")
  List<UserReferenceDto> findAllReference();

  @NotNull @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  Optional<User> findById(@NotNull UUID id);

  Optional<User> findByEmail(String email);

  @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  Optional<User> findByUsername(String username);

  @EntityGraph(attributePaths = {"roles"})
  Optional<User> findByUsernameIgnoreCaseOrDisplayNameIgnoreCase(
      String username, String displayName);

  @EntityGraph(attributePaths = {"roles"})
  List<User> findAllByUsernameIgnoreCaseOrDisplayNameIgnoreCase(
      String username, String displayName);

  @EntityGraph(attributePaths = {"roles"})
  List<User> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
      String username, String displayName);

  @EntityGraph(attributePaths = {"roles"})
  Page<User> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
      String username, String displayName, Pageable pageable);

  @EntityGraph(attributePaths = {"roles"})
  Page<User> findAll(Pageable pageable);

  @org.springframework.data.jpa.repository.Modifying
  @Query("UPDATE User u SET u.inKeycloak = false WHERE u.id NOT IN :ids")
  void markMissingUsers(@NotNull java.util.Collection<java.util.UUID> ids);

  @Query("SELECT u FROM User u JOIN u.roles r WHERE UPPER(r.name) = 'ADMIN' ORDER BY u.username")
  List<User> findAllAdmins();
}
