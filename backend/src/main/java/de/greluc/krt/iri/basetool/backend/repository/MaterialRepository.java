package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialPriceOverviewDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Material. */
@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {

  /**
   * Returns slim {@code MaterialReferenceDto}s (id, name, quantity-type) for every material,
   * ordered by name. Used to populate material pickers without pulling the full Material aggregate.
   */
  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.dto.MaterialReferenceDto(m.id, m.name,"
          + " m.quantityType) FROM Material m ORDER BY m.name")
  List<de.greluc.krt.iri.basetool.backend.model.dto.MaterialReferenceDto> findAllReference();

  /**
   * Returns every entity matching the derived {@code findAllByIsJobOrderTrueOrderByNameAsc}
   * criteria.
   */
  List<Material> findAllByIsJobOrderTrueOrderByNameAsc();

  /** Derived Spring-Data query - returns entities matching {@code IdCommodity}. */
  Optional<Material> findByIdCommodity(Integer idCommodity);

  /** Derived Spring-Data query - returns entities matching {@code Name}. */
  Optional<Material> findByName(String name);

  /**
   * Returns only the materials that actually have at least one price row at a non-hidden terminal -
   * useful to suppress materials with no buy/sell data in the trade UI.
   */
  @Query(
      "SELECT m FROM Material m WHERE EXISTS (SELECT 1 FROM MaterialPrice p WHERE p.material = m"
          + " AND (p.terminal.hidden = false OR p.terminal.hidden IS NULL))")
  Page<Material> findAllWithPrices(Pageable pageable);

  /**
   * Per-material price summary used by the price-overview view: best (minimum) positive buy price
   * and best (maximum) positive sell price across every non-hidden terminal, plus the material's
   * category and UEX-style flag columns flattened into the DTO. {@code name} is an optional
   * case-insensitive substring filter.
   */
  @Query(
      """
      SELECT new de.greluc.krt.iri.basetool.backend.model.dto.MaterialPriceOverviewDto(
          m.id, m.name, c.id, c.name, c.version,
          m.isIllegal, m.isVolatileQt, m.isVolatileTime,
          MIN(CASE WHEN p.priceBuy > 0 THEN p.priceBuy ELSE null END),
          MAX(CASE WHEN p.priceSell > 0 THEN p.priceSell ELSE null END)
      )
      FROM Material m
      LEFT JOIN m.category c
      JOIN MaterialPrice p ON p.material = m
      WHERE (cast(:name as string) IS NULL
          OR LOWER(m.name) LIKE LOWER(CONCAT('%', cast(:name as string), '%')))
      AND (p.terminal.hidden = false OR p.terminal.hidden IS NULL)
      GROUP BY m.id, m.name, c.id, c.name, c.version,
          m.isIllegal, m.isVolatileQt, m.isVolatileTime
      """)
  Page<MaterialPriceOverviewDto> getMaterialPriceOverview(
      @Param("name") String name, Pageable pageable);
}
