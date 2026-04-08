package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    @EntityGraph(attributePaths = {"permissions"})
    Optional<Role> findByName(String name);

    @EntityGraph(attributePaths = {"permissions"})
    Optional<Role> findByNameIgnoreCase(String name);

    @NotNull
    @EntityGraph(attributePaths = {"permissions"})
    Page<Role> findAll(@NotNull Pageable pageable);
}
