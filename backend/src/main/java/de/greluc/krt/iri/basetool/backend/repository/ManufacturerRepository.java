package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
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

/** Spring Data repository for Manufacturer. */
@Repository
public interface ManufacturerRepository extends JpaRepository<Manufacturer, UUID> {
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

  /** Derived Spring-Data query - returns entities matching {@code NameIgnoreCase}. */
  Optional<Manufacturer> findByNameIgnoreCase(String name);

  /**
   * Resolution-chain step 1 for the R2 {@code UexManufacturerService}: match by UEX's integer
   * company id. UEX never re-numbers companies, so this is the fastest re-resolution key.
   *
   * @param uexCompanyId UEX integer company id (from {@code /companies[].id})
   * @return matching manufacturer if present
   */
  Optional<Manufacturer> findByUexCompanyId(Integer uexCompanyId);

  /** Derived Spring-Data query - returns entities matching {@code HiddenFalse}. */
  Page<Manufacturer> findByHiddenFalse(Pageable pageable);

  /**
   * Resolution-chain step 1 for the R5 Wiki item backfill: match an inbound Wiki item's nested
   * manufacturer by the Wiki manufacturer UUID stored on the local row. Used only to attach a
   * manufacturer to a freshly created {@code WIKI_ONLY} {@code game_item}; existing rows keep their
   * (sticky) UEX manufacturer. Never creates a row — an unmatched manufacturer is left {@code null}
   * for the dedicated R6 reconciliation.
   *
   * @param scwikiUuid Wiki manufacturer UUID (from the item payload's {@code manufacturer.uuid})
   * @return matching manufacturer if present
   */
  Optional<Manufacturer> findByScwikiUuid(UUID scwikiUuid);

  /**
   * Resolution-chain fallback for the R6 manufacturer reconciliation: match a Wiki manufacturer by
   * its short {@code code} (e.g. {@code "AEGS"}) against the local UNIQUE {@code abbreviation} when
   * the case-insensitive name match missed (UEX and Wiki occasionally spell the full name
   * differently while sharing the code).
   *
   * @param abbreviation manufacturer short code / abbreviation
   * @return matching manufacturer if present
   */
  Optional<Manufacturer> findByAbbreviationIgnoreCase(String abbreviation);

  /**
   * Soft-deletes SC Wiki ownership of every <em>Wiki-linked</em> manufacturer ({@code
   * scwiki_synced_at IS NOT NULL}) whose {@code scwiki_uuid} is NOT in {@code seenScwikiUuids} and
   * that is not already marked. Drives the R6 orphan sweep (SC_WIKI_SYNC_PLAN.md §8.7); the caller
   * gates it on a non-empty seen set so an empty / failed Wiki fetch never wipes the reconciliation
   * state. The UEX-canonical columns are untouched — only the soft-delete marker is set.
   *
   * @param seenScwikiUuids the Wiki manufacturer UUIDs reconciled this run
   * @param now timestamp to stamp on the soft-deleted rows
   * @return number of rows marked deleted
   */
  @Modifying
  @Query(
      "UPDATE Manufacturer m SET m.scwikiDeletedAt = :now "
          + "WHERE m.scwikiSyncedAt IS NOT NULL "
          + "AND m.scwikiUuid NOT IN :seenScwikiUuids "
          + "AND m.scwikiDeletedAt IS NULL")
  int markScwikiDeletedExcept(
      @Param("seenScwikiUuids") Collection<UUID> seenScwikiUuids, @Param("now") Instant now);
}
