package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Inventory Item. */
@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

  /**
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
   */
  @EntityGraph(attributePaths = {"material", "location", "user", "jobOrder", "mission"})
  @Query("SELECT i FROM InventoryItem i")
  List<InventoryItem> findAllWithEagerRelationships();

  /**
   * Derived Spring-Data query - returns entities matching {@code User}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"material", "location", "user", "jobOrder", "mission"})
  Page<InventoryItem> findByUser(User user, Pageable pageable);

  /** Derived Spring-Data query - returns entities matching {@code MaterialAndPersonalFalse}. */
  Page<InventoryItem> findByMaterialAndPersonalFalse(Material material, Pageable pageable);

  /** Derived Spring-Data query - returns entities matching {@code PersonalFalse}. */
  Page<InventoryItem> findByPersonalFalse(Pageable pageable);

  /**
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
   */
  @EntityGraph(attributePaths = {"material", "location", "user", "jobOrder", "mission"})
  @Query(
      "SELECT i FROM InventoryItem i WHERE i.personal = false "
          + "AND (:hasMaterials = false OR i.material.id IN :materialIds) "
          + "AND (:minQuality IS NULL OR i.quality >= :minQuality) "
          + "AND (:hasJobOrders = false OR (i.jobOrder IS NOT NULL AND i.jobOrder.id IN :jobOrderIds)) "
          + "AND (:hasMissions = false OR (i.mission IS NOT NULL AND i.mission.id IN :missionIds))")
  Page<InventoryItem> findGlobalByFilters(
      @Param("hasMaterials") boolean hasMaterials,
      @Param("materialIds") List<UUID> materialIds,
      @Param("minQuality") Integer minQuality,
      @Param("hasJobOrders") boolean hasJobOrders,
      @Param("jobOrderIds") List<UUID> jobOrderIds,
      @Param("hasMissions") boolean hasMissions,
      @Param("missionIds") List<UUID> missionIds,
      Pageable pageable);

  /**
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
   */
  @EntityGraph(attributePaths = {"material", "location", "user", "jobOrder", "mission"})
  @Query(
      "SELECT i FROM InventoryItem i WHERE i.user = :user "
          + "AND (:hasMaterials = false OR i.material.id IN :materialIds) "
          + "AND (:minQuality IS NULL OR i.quality >= :minQuality) "
          + "AND (:hasJobOrders = false OR (i.jobOrder IS NOT NULL AND i.jobOrder.id IN :jobOrderIds)) "
          + "AND (:hasMissions = false OR (i.mission IS NOT NULL AND i.mission.id IN :missionIds))")
  Page<InventoryItem> findUserByFilters(
      @Param("user") User user,
      @Param("hasMaterials") boolean hasMaterials,
      @Param("materialIds") List<UUID> materialIds,
      @Param("minQuality") Integer minQuality,
      @Param("hasJobOrders") boolean hasJobOrders,
      @Param("jobOrderIds") List<UUID> jobOrderIds,
      @Param("hasMissions") boolean hasMissions,
      @Param("missionIds") List<UUID> missionIds,
      Pageable pageable);

  /**
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
   */
  @Query(
      "SELECT i.material as material, "
          + "CASE WHEN SUM(i.amount) > 0 THEN SUM(CAST(i.quality AS double) * i.amount) / SUM(i.amount) ELSE 0.0 END as quality, "
          + "SUM(i.amount) as amount "
          + "FROM InventoryItem i WHERE i.personal = false GROUP BY i.material")
  Page<Object[]> getAggregatedInventory(Pageable pageable);

  /**
   * Derived Spring-Data query - returns entities matching {@code JobOrderIdAndMaterialId}. Eagerly
   * fetches the configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"user", "location", "material"})
  List<InventoryItem> findByJobOrderIdAndMaterialId(UUID jobOrderId, UUID materialId);

  /** Derived Spring-Data query - returns entities matching {@code JobOrderIdOrdered}. */
  @EntityGraph(attributePaths = {"user", "location", "material"})
  @Query(
      "SELECT i FROM InventoryItem i WHERE i.jobOrder.id = :jobOrderId ORDER BY i.user.username ASC, i.location.name ASC, i.material.name ASC, i.quality DESC, i.amount DESC")
  List<InventoryItem> findByJobOrderIdOrdered(@Param("jobOrderId") UUID jobOrderId);

  /**
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
   */
  @Query(
      value =
          "SELECT COALESCE(SUM(amount), 0.0) FROM inventory_item "
              + "WHERE material_id = :materialId "
              + "AND job_order_id = :jobOrderId "
              + "AND quality >= :minQuality",
      nativeQuery = true)
  Double sumAmountByMaterialAndJobOrderAndMinQuality(
      @Param("materialId") UUID materialId,
      @Param("jobOrderId") UUID jobOrderId,
      @Param("minQuality") Integer minQuality);

  /**
   * Custom JPQL/native bulk update; see the {@code @Query} annotation for the WHERE clause and the
   * {@code @Param} contract.
   */
  @Modifying
  @Query("UPDATE InventoryItem i SET i.jobOrder = null WHERE i.jobOrder.id = :jobOrderId")
  void unlinkJobOrder(@Param("jobOrderId") UUID jobOrderId);

  /**
   * Custom JPQL/native bulk update; see the {@code @Query} annotation for the WHERE clause and the
   * {@code @Param} contract.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE InventoryItem i SET i.jobOrder = null WHERE i.jobOrder.id = :jobOrderId AND i.material.id = :materialId")
  void unlinkJobOrderMaterial(
      @Param("jobOrderId") UUID jobOrderId, @Param("materialId") UUID materialId);

  /**
   * Custom JPQL/native bulk update; see the {@code @Query} annotation for the WHERE clause and the
   * {@code @Param} contract.
   */
  @Modifying
  @Query("UPDATE InventoryItem i SET i.mission = null WHERE i.mission.id IN :missionIds")
  void unlinkMissions(@Param("missionIds") List<UUID> missionIds);

  /**
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
   */
  @Query(
      "SELECT i FROM InventoryItem i WHERE "
          + "i.user = :user AND "
          + "i.material = :material AND "
          + "i.location = :location AND "
          + "i.quality = :quality AND "
          + "((i.mission IS NULL AND :mission IS NULL) OR (i.mission = :mission)) AND "
          + "((i.jobOrder IS NULL AND :jobOrder IS NULL) OR (i.jobOrder = :jobOrder)) AND "
          + "((i.personal IS NULL AND :personal IS NULL) OR (i.personal = :personal))")
  java.util.List<InventoryItem> findMatchingInventoryItem(
      @Param("user") User user,
      @Param("material") Material material,
      @Param("location") Location location,
      @Param("quality") Integer quality,
      @Param("mission") Mission mission,
      @Param("jobOrder") JobOrder jobOrder,
      @Param("personal") Boolean personal);

  /**
   * Custom JPQL/native bulk update; see the {@code @Query} annotation for the WHERE clause and the
   * {@code @Param} contract.
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE InventoryItem i SET i.user = :newUser WHERE i.user = :oldUser")
  void updateOwner(
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User oldUser,
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User newUser);

  /**
   * Derived Spring-Data query - returns entities matching {@code IdForUpdate}. Acquires a
   * pessimistic write lock for the duration of the surrounding transaction.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"material", "jobOrder", "user", "location"})
  @Query("SELECT i FROM InventoryItem i WHERE i.id = :id")
  Optional<InventoryItem> findByIdForUpdate(@Param("id") UUID id);
}
