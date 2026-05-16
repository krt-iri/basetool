package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MaterialPrice;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialMatrixItemDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialPriceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialSellingTerminalDto;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Material Price. */
@Repository
public interface MaterialPriceRepository extends JpaRepository<MaterialPrice, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code MaterialIdAndTerminalId}. */
  Optional<MaterialPrice> findByMaterialIdAndTerminalId(UUID materialId, UUID terminalId);

  /**
   * Returns paginated buy/sell prices for one material across every non-hidden terminal, projected
   * directly into {@link MaterialPriceDto} (no need to fetch the full {@link MaterialPrice} graph).
   */
  @Query(
      """
          SELECT new de.greluc.krt.iri.basetool.backend.model.dto.MaterialPriceDto(
              p.id, t.name, p.priceBuy, p.priceSell, p.scuBuy, p.scuSell, p.statusBuy, p.statusSell
          )
          FROM MaterialPrice p
          JOIN p.terminal t
          WHERE p.material.id = :materialId
          AND (t.hidden = false OR t.hidden IS NULL)
      """)
  Page<MaterialPriceDto> findPricesByMaterialId(
      @Param("materialId") UUID materialId, Pageable pageable);

  /**
   * Returns every non-hidden terminal that currently buys the given material, ordered by sell price
   * descending (best price first; {@code NULLS LAST} so terminals with unknown price land at the
   * bottom). A terminal qualifies if {@code statusSell = true} or its {@code priceSell} is positive
   * - the OR catches both UEX import paths.
   */
  @Query(
      """
          SELECT new de.greluc.krt.iri.basetool.backend.model.dto.MaterialSellingTerminalDto(
              t.id, t.name, p.priceSell
          )
          FROM MaterialPrice p
          JOIN p.terminal t
          WHERE p.material.id = :materialId
          AND (p.statusSell = true OR p.priceSell > 0)
          AND (t.hidden = false OR t.hidden IS NULL)
          ORDER BY p.priceSell DESC NULLS LAST, t.name ASC
      """)
  java.util.List<MaterialSellingTerminalDto> findSellingTerminalsByMaterialId(
      @Param("materialId") UUID materialId);

  /**
   * Fully flattened material/terminal/price tuple feeding the trade-matrix view. {@code
   * isIllegal/isVolatileQt/isVolatileTime} are normalised from UEX-style {@code Integer} 0/1 flags
   * into booleans inside the JPQL via {@code CASE}; the category is left-joined because not every
   * material has one. Excludes hidden terminals.
   *
   * <p>The projected {@code planetName} is the <i>effective</i> planet-system anchor for a
   * terminal, resolved in this order via {@code COALESCE}:
   *
   * <ol>
   *   <li>{@code terminal.planet_name} (set directly when the terminal sits on a planet or a
   *       station in that planet's orbit),
   *   <li>{@code moon.planet_name} via {@code moon.name = terminal.moon_name} - covers terminals on
   *       moons whose parent planet is only indirectly known,
   *   <li>{@code planet.name} where the planet's own name matches {@code terminal.orbit_name} in
   *       the same star system - covers Lagrange-style orbits named after their host planet.
   * </ol>
   *
   * <p>The result is {@code null} for true system-level terminals (e.g. raw jump-point or
   * interplanetary Lagrange stations) that have no parent planet at all.
   */
  @Query(
      """
      SELECT new de.greluc.krt.iri.basetool.backend.model.dto.MaterialMatrixItemDto(
          m.id, m.name, CASE WHEN m.isIllegal = 1 THEN true ELSE false END,
          CASE WHEN m.isVolatileQt = 1 THEN true ELSE false END,
          CASE WHEN m.isVolatileTime = 1 THEN true ELSE false END,
          c.id, c.name, c.version, t.id, t.name, t.nickname, t.starSystemName,
          p.priceBuy, p.priceSell,
          t.cityName, t.spaceStationName, t.outpostName,
          COALESCE(t.planetName, mn.planetName, pl.name),
          t.isJumpPoint, t.hasLoadingDock,
          t.isAutoLoad
      )
      FROM MaterialPrice p
      JOIN p.material m
      LEFT JOIN m.category c
      JOIN p.terminal t
      LEFT JOIN Moon mn ON mn.name = t.moonName AND mn.starSystemName = t.starSystemName
      LEFT JOIN Planet pl ON pl.name = t.orbitName AND pl.starSystemName = t.starSystemName
      WHERE (t.hidden = false OR t.hidden IS NULL)
      """)
  Page<MaterialMatrixItemDto> findAllMatrixItems(Pageable pageable);

  /**
   * Returns every price row whose terminal supports cargo auto-load (i.e. usable as a profit-run
   * destination), eagerly joining material and terminal so the profit calculator can iterate
   * without N+1 queries.
   */
  @Query(
      """
          SELECT p
          FROM MaterialPrice p
          JOIN FETCH p.material m
          JOIN FETCH p.terminal t
          WHERE (t.hidden = false OR t.hidden IS NULL)
          AND t.isAutoLoad = true
      """)
  java.util.List<MaterialPrice> findAllAutoLoadPrices();

  /**
   * Same as {@link #findAllAutoLoadPrices} but restricted to terminals in the given star systems -
   * used when the profit run is constrained to a subset of the universe.
   */
  @Query(
      """
          SELECT p
          FROM MaterialPrice p
          JOIN FETCH p.material m
          JOIN FETCH p.terminal t
          WHERE (t.hidden = false OR t.hidden IS NULL)
          AND t.isAutoLoad = true
          AND t.starSystemName IN :starSystems
      """)
  java.util.List<MaterialPrice> findAllAutoLoadPricesInSystems(
      @Param("starSystems") java.util.Collection<String> starSystems);
}
