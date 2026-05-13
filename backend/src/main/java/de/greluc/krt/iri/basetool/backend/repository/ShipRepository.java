package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Ship;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipRepository extends JpaRepository<Ship, UUID> {

  @Modifying(clearAutomatically = true)
  @Query("UPDATE Ship s SET s.fitted = false")
  void resetAllFitted();

  boolean existsByShipTypeId(UUID shipTypeId);

  boolean existsByLocationId(UUID locationId);

  boolean existsByOwnerIdAndShipTypeId(UUID ownerId, UUID shipTypeId);

  long countByOwnerIdAndShipTypeId(UUID ownerId, UUID shipTypeId);

  @EntityGraph(attributePaths = {"shipType", "location", "owner"})
  List<Ship> findByOwnerId(UUID ownerId);

  @EntityGraph(attributePaths = {"shipType", "location", "owner"})
  Page<Ship> findByOwnerId(UUID ownerId, Pageable pageable);

  @EntityGraph(attributePaths = {"shipType", "location", "owner"})
  Page<Ship> findAll(Pageable pageable);

  @Query(
      "SELECT s.shipType, COUNT(s), SUM(CASE WHEN s.fitted = true THEN 1 ELSE 0 END) FROM Ship s GROUP BY s.shipType ORDER BY s.shipType.name ASC")
  Page<Object[]> countShipsByType(Pageable pageable);

  @EntityGraph(attributePaths = {"owner", "location"})
  List<Ship> findByShipTypeIn(List<de.greluc.krt.iri.basetool.backend.model.ShipType> shipTypes);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE Ship s SET s.owner = :newUser WHERE s.owner = :oldUser")
  void updateOwner(
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User oldUser,
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User newUser);
}
