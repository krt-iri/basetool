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

/** Spring Data repository for Ship. */
@Repository
public interface ShipRepository extends JpaRepository<Ship, UUID> {

  /**
   * Custom JPQL/native bulk update; see the {@code @Query} annotation for the WHERE clause and the
   * {@code @Param} contract.
   */
  @Modifying(clearAutomatically = true)
  @Query("UPDATE Ship s SET s.fitted = false")
  void resetAllFitted();

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * ShipTypeId}.
   */
  boolean existsByShipTypeId(UUID shipTypeId);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * LocationId}.
   */
  boolean existsByLocationId(UUID locationId);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * OwnerIdAndShipTypeId}.
   */
  boolean existsByOwnerIdAndShipTypeId(UUID ownerId, UUID shipTypeId);

  /**
   * Derived Spring-Data query - returns the count of rows matching {@code OwnerIdAndShipTypeId}.
   */
  long countByOwnerIdAndShipTypeId(UUID ownerId, UUID shipTypeId);

  /**
   * Derived Spring-Data query - returns entities matching {@code OwnerId}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"shipType", "location", "owner"})
  List<Ship> findByOwnerId(UUID ownerId);

  /**
   * Derived Spring-Data query - returns entities matching {@code OwnerId}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"shipType", "location", "owner"})
  Page<Ship> findByOwnerId(UUID ownerId, Pageable pageable);

  /**
   * Lists every entity. Overridden here to attach an {@code @EntityGraph}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"shipType", "location", "owner"})
  Page<Ship> findAll(Pageable pageable);

  /**
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
   */
  @Query(
      "SELECT s.shipType, COUNT(s), SUM(CASE WHEN s.fitted = true THEN 1 ELSE 0 END) FROM Ship s GROUP BY s.shipType ORDER BY s.shipType.name ASC")
  Page<Object[]> countShipsByType(Pageable pageable);

  /**
   * Derived Spring-Data query - returns entities matching {@code ShipTypeIn}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location"})
  List<Ship> findByShipTypeIn(List<de.greluc.krt.iri.basetool.backend.model.ShipType> shipTypes);

  /**
   * Custom JPQL/native bulk update; see the {@code @Query} annotation for the WHERE clause and the
   * {@code @Param} contract.
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE Ship s SET s.owner = :newUser WHERE s.owner = :oldUser")
  void updateOwner(
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User oldUser,
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User newUser);
}
