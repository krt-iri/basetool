package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialPriceOverviewDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {
    
    @Query("SELECT new de.greluc.krt.iri.basetool.backend.model.dto.MaterialReferenceDto(m.id, m.name) FROM Material m ORDER BY m.name")
    List<de.greluc.krt.iri.basetool.backend.model.dto.MaterialReferenceDto> findAllReference();
    
    Optional<Material> findByIdCommodity(Integer idCommodity);
    Optional<Material> findByName(String name);

    @Query("SELECT m FROM Material m WHERE EXISTS (SELECT 1 FROM MaterialPrice p WHERE p.material = m AND (p.terminal.hidden = false OR p.terminal.hidden IS NULL))")
    Page<Material> findAllWithPrices(Pageable pageable);

    @Query("""
        SELECT new de.greluc.krt.iri.basetool.backend.model.dto.MaterialPriceOverviewDto(
            m.id, m.name, c.id, c.name, c.version, 
            m.isIllegal, m.isVolatileQt, m.isVolatileTime,
            MIN(CASE WHEN p.priceBuy > 0 THEN p.priceBuy ELSE null END), 
            MAX(CASE WHEN p.priceSell > 0 THEN p.priceSell ELSE null END)
        )
        FROM Material m
        LEFT JOIN m.category c
        JOIN MaterialPrice p ON p.material = m
        WHERE (cast(:name as string) IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', cast(:name as string), '%')))
        AND (p.terminal.hidden = false OR p.terminal.hidden IS NULL)
        GROUP BY m.id, m.name, c.id, c.name, c.version, m.isIllegal, m.isVolatileQt, m.isVolatileTime
    """)
    Page<MaterialPriceOverviewDto> getMaterialPriceOverview(@Param("name") String name, Pageable pageable);
}
