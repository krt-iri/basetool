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
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
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
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
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
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
   */
  @Query(
      """
        SELECT new de.greluc.krt.iri.basetool.backend.model.dto.MaterialMatrixItemDto(
            m.id, m.name, CASE WHEN m.isIllegal = 1 THEN true ELSE false END,
            CASE WHEN m.isVolatileQt = 1 THEN true ELSE false END,
            CASE WHEN m.isVolatileTime = 1 THEN true ELSE false END,
            c.id, c.name, c.version, t.id, t.name, t.nickname, t.starSystemName, p.priceBuy, p.priceSell,
            t.cityName, t.spaceStationName, t.outpostName, t.isJumpPoint, t.hasLoadingDock, t.isAutoLoad
        )
        FROM MaterialPrice p
        JOIN p.material m
        LEFT JOIN m.category c
        JOIN p.terminal t
        WHERE (t.hidden = false OR t.hidden IS NULL)
    """)
  Page<MaterialMatrixItemDto> findAllMatrixItems(Pageable pageable);

  /**
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
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
   * Custom JPQL/native query; see the {@code @Query} annotation for the projection and filter
   * clauses.
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
