package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialPrice;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.Terminal;
import de.greluc.krt.iri.basetool.backend.model.dto.ProfitCalculationDto;
import de.greluc.krt.iri.basetool.backend.repository.MaterialPriceRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfitCalculationServiceTest {

    @Mock
    private MaterialPriceRepository materialPriceRepository;

    @Mock
    private ShipTypeRepository shipTypeRepository;

    @InjectMocks
    private ProfitCalculationService profitCalculationService;

    @Test
    void shouldCalculateProfitAndSortByMaterialName() {
        // Given
        UUID shipId = UUID.randomUUID();
        ShipType ship = new ShipType();
        ship.setId(shipId);
        ship.setName("C2 Hercules");
        ship.setScu(696);
        
        Material m1 = new Material();
        m1.setId(UUID.randomUUID());
        m1.setName("Laranite");
        
        Material m2 = new Material();
        m2.setId(UUID.randomUUID());
        m2.setName("Agricium");
        
        Material m3 = new Material();
        m3.setId(UUID.randomUUID());
        m3.setName("Zeyneh");

        Terminal t = new Terminal();
        t.setIsAutoLoad(true);
        t.setStarSystemName("Stanton");
        
        MaterialPrice p1 = new MaterialPrice();
        p1.setMaterial(m1);
        p1.setTerminal(t);
        p1.setPriceBuy(BigDecimal.valueOf(20));
        p1.setPriceSell(BigDecimal.valueOf(30));
        
        MaterialPrice p2 = new MaterialPrice();
        p2.setMaterial(m2);
        p2.setTerminal(t);
        p2.setPriceBuy(BigDecimal.valueOf(10));
        p2.setPriceSell(BigDecimal.valueOf(15));

        MaterialPrice p3 = new MaterialPrice();
        p3.setMaterial(m3);
        p3.setTerminal(t);
        p3.setPriceBuy(BigDecimal.valueOf(5));
        p3.setPriceSell(BigDecimal.valueOf(20));

        when(shipTypeRepository.findById(shipId)).thenReturn(Optional.of(ship));
        when(materialPriceRepository.findAllAutoLoadPrices()).thenReturn(List.of(p1, p2, p3));

        // When
        List<ProfitCalculationDto> result = profitCalculationService.calculateProfit(shipId, null);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        
        // Sollte alphabetisch sortiert sein: Agricium, Laranite, Zeyneh
        assertEquals("Agricium", result.get(0).materialName());
        assertEquals("Laranite", result.get(1).materialName());
        assertEquals("Zeyneh", result.get(2).materialName());
    }
}
