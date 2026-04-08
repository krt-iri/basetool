package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexCommodityPriceDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialPrice;
import de.greluc.krt.iri.basetool.backend.model.Terminal;
import de.greluc.krt.iri.basetool.backend.repository.MaterialPriceRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.TerminalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UexCommodityServiceTest {

    @Mock
    private UexClient uexClient;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialPriceRepository materialPriceRepository;

    @Mock
    private TerminalRepository terminalRepository;

    @InjectMocks
    private UexCommodityService uexCommodityService;

    @Test
    void shouldProcessCommodityDtoAndCreateNewMaterialAndLocation() {
        // Given
        UexCommodityPriceDto dto = UexCommodityPriceDto.builder()
                .idCommodity(1)
                .commodityName("Laranite")
                .idTerminal(10)
                .terminalName("Area18")
                .priceBuy(BigDecimal.valueOf(25.5))
                .priceSell(BigDecimal.valueOf(30.0))
                .build();

        when(uexClient.getCommoditiesPricesAll()).thenReturn(List.of(dto));
        when(materialRepository.findByIdCommodity(1)).thenReturn(Optional.empty());
        when(materialRepository.findByName("Laranite")).thenReturn(Optional.empty());
        
        Material savedMaterial = new Material();
        savedMaterial.setId(UUID.randomUUID());
        when(materialRepository.save(any(Material.class))).thenReturn(savedMaterial);

        Terminal mockTerminal = new Terminal();
        mockTerminal.setId(UUID.randomUUID());
        mockTerminal.setIdTerminal(10);
        mockTerminal.setCityName("Area18");
        when(terminalRepository.findByIdTerminal(10)).thenReturn(Optional.of(mockTerminal));

        when(materialPriceRepository.findByMaterialIdAndTerminalId(savedMaterial.getId(), mockTerminal.getId()))
                .thenReturn(Optional.empty());

        // When
        uexCommodityService.fetchAndProcessCommoditiesPrices();

        // Then
        ArgumentCaptor<MaterialPrice> priceCaptor = ArgumentCaptor.forClass(MaterialPrice.class);
        verify(materialPriceRepository).save(priceCaptor.capture());
        
        MaterialPrice savedPrice = priceCaptor.getValue();
        assertEquals(BigDecimal.valueOf(25.5), savedPrice.getPriceBuy());
        assertEquals(BigDecimal.valueOf(30.0), savedPrice.getPriceSell());
    }
}
