package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

  /**
   * Resolution-chain fallback for {@code UexManufacturerService}: match by the UNIQUE {@code
   * abbreviation} (the UEX nickname) when both the {@code uexCompanyId} and {@code name} lookups
   * miss. {@code abbreviation} is the column under the UNIQUE constraint, so a legacy row whose
   * {@code name} is the short form — local {@code "Esperia"} vs UEX {@code "Esperia Incorporation"}
   * — must be adopted here rather than inserted as a duplicate that violates {@code
   * manufacturer_abbreviation_key}.
   *
   * @param abbreviation manufacturer short code / abbreviation (the UEX nickname)
   * @return matching manufacturer if present
   */
  Optional<Manufacturer> findByAbbreviationIgnoreCase(String abbreviation);

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
}
