package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.ShipType;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Ship Type. */
@Repository
public interface ShipTypeRepository extends JpaRepository<ShipType, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code NameIgnoreCase}. */
  Optional<ShipType> findByNameIgnoreCase(String name);

  /**
   * Resolution-chain step 1 (R2): match an inbound UEX vehicle DTO to an existing ship_type row via
   * the shared in-game asset UUID.
   *
   * @param externalUuid in-game asset UUID
   * @return matching {@link ShipType} if present
   */
  Optional<ShipType> findByExternalUuid(UUID externalUuid);

  /**
   * Resolution-chain step 2 (R2): match by UEX's integer vehicle id. Used when the UEX payload
   * carries no UUID but the row was previously created.
   *
   * @param uexVehicleId UEX integer vehicle id
   * @return matching {@link ShipType} if present
   */
  Optional<ShipType> findByUexVehicleId(Integer uexVehicleId);

  /**
   * Soft-deletes UEX-side ownership of every row whose {@code uex_vehicle_id} is set, NOT included
   * in {@code seenIds}, and whose {@code uex_deleted_at} is currently NULL. Gated by a non-empty
   * {@code seenIds} so a failed sync run does not wipe local data.
   *
   * @param seenIds UEX vehicle ids successfully processed in the current run
   * @param now timestamp to stamp on the soft-deleted rows
   * @return number of rows marked deleted
   */
  @Modifying
  @Query(
      "UPDATE ShipType s SET s.uexDeletedAt = :now "
          + "WHERE s.uexVehicleId IS NOT NULL "
          + "AND s.uexVehicleId NOT IN :seenIds "
          + "AND s.uexDeletedAt IS NULL")
  int markUexDeletedExcept(
      @Param("seenIds") Collection<Integer> seenIds, @Param("now") Instant now);

  /**
   * Soft-deletes SC Wiki ownership of every ship_type row the Wiki has actually written ({@code
   * scwiki_synced_at IS NOT NULL}) that is NOT in {@code seenExternalUuids} and not already marked
   * (R4 §8.6 / §8.7). Gated by the caller on a non-empty seen set so a failed / empty Wiki fetch
   * never wipes the Wiki-side merge state.
   *
   * <p>Gating on {@code scwiki_synced_at} (not merely {@code external_uuid IS NOT NULL}) is
   * deliberate and matches {@code GameItemRepository.markScwikiDeletedExcept}: a UEX-only vehicle
   * also carries an {@code external_uuid} (stamped by the UEX vehicle sync), so the looser
   * predicate would spuriously stamp {@code scwiki_deleted_at} — "missing from Wiki since …" — on a
   * row the Wiki has never described (e.g. UEX-only capital ships like Idris-M / Polaris, §8.3.3).
   *
   * @param seenExternalUuids the external UUIDs the Wiki vehicle sync touched this run
   * @param now timestamp to stamp on the soft-deleted rows
   * @return number of rows marked deleted
   */
  @Modifying
  @Query(
      "UPDATE ShipType s SET s.scwikiDeletedAt = :now "
          + "WHERE s.scwikiSyncedAt IS NOT NULL "
          + "AND s.externalUuid NOT IN :seenExternalUuids "
          + "AND s.scwikiDeletedAt IS NULL")
  int markScwikiDeletedExcept(
      @Param("seenExternalUuids") Collection<UUID> seenExternalUuids, @Param("now") Instant now);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * NameIgnoreCase}.
   */
  boolean existsByNameIgnoreCase(String name);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * NameIgnoreCaseAndIdNot}.
   */
  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * ManufacturerId}.
   */
  boolean existsByManufacturerId(UUID manufacturerId);

  /** Derived Spring-Data query - returns entities matching {@code HiddenFalse}. */
  Page<ShipType> findByHiddenFalse(Pageable pageable);
}
