package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialPrice;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.dto.ProfitCalculationDto;
import de.greluc.krt.iri.basetool.backend.repository.MaterialPriceRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfitCalculationService {

    private final MaterialPriceRepository materialPriceRepository;
    private final ShipTypeRepository shipTypeRepository;

    @Transactional(readOnly = true)
    public List<ProfitCalculationDto> calculateProfit(UUID shipId, List<String> starSystemNames) {
        log.debug("Calculating profit for shipId: {} and starSystems: {}", shipId, starSystemNames);

        ShipType ship = shipTypeRepository.findById(shipId)
                .orElseThrow(() -> new IllegalArgumentException("ShipType not found: " + shipId));

        int shipScu = ship.getScu() != null ? ship.getScu() : 0;
        boolean isHullC = ship.getName() != null && ship.getName().toLowerCase().contains("hull c");

        log.debug("Ship: {}, SCU: {}, isHullC: {}", ship.getName(), shipScu, isHullC);

        List<MaterialPrice> prices;
        if (starSystemNames == null || starSystemNames.isEmpty()) {
            prices = materialPriceRepository.findAllAutoLoadPrices();
        } else {
            prices = materialPriceRepository.findAllAutoLoadPricesInSystems(starSystemNames);
        }

        log.debug("Found {} potential prices before Hull C filtering", prices.size());

        if (isHullC) {
            prices = prices.stream()
                    .filter(p -> p.getTerminal().getHasLoadingDock() != null && p.getTerminal().getHasLoadingDock())
                    .toList();
            log.debug("Hull C filtering reduced prices to {}", prices.size());
        }

        Map<Material, List<MaterialPrice>> pricesByMaterial = prices.stream()
                .collect(Collectors.groupingBy(MaterialPrice::getMaterial));

        List<ProfitCalculationDto> results = new ArrayList<>();

        for (Map.Entry<Material, List<MaterialPrice>> entry : pricesByMaterial.entrySet()) {
            Material material = entry.getKey();
            List<MaterialPrice> materialPrices = entry.getValue();

            BigDecimal minBuy = materialPrices.stream()
                    .map(MaterialPrice::getPriceBuy)
                    .filter(Objects::nonNull)
                    .filter(p -> p.compareTo(BigDecimal.ZERO) > 0)
                    .min(BigDecimal::compareTo)
                    .orElse(null);

            BigDecimal maxSell = materialPrices.stream()
                    .map(MaterialPrice::getPriceSell)
                    .filter(Objects::nonNull)
                    .filter(p -> p.compareTo(BigDecimal.ZERO) > 0)
                    .max(BigDecimal::compareTo)
                    .orElse(null);

            if (minBuy != null && maxSell != null) {
                BigDecimal profitPerScu = maxSell.subtract(minBuy);
                BigDecimal marginPercent = minBuy.compareTo(BigDecimal.ZERO) > 0 
                        ? profitPerScu.divide(minBuy, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;
                BigDecimal fullLoadCost = minBuy.multiply(BigDecimal.valueOf(shipScu));
                BigDecimal maxProfitFullLoad = profitPerScu.multiply(BigDecimal.valueOf(shipScu));

                results.add(new ProfitCalculationDto(
                        material.getId(),
                        material.getName(),
                        minBuy,
                        maxSell,
                        profitPerScu,
                        marginPercent,
                        fullLoadCost,
                        maxProfitFullLoad
                ));
            }
        }

        results.sort(Comparator.comparing(ProfitCalculationDto::materialName));
        log.debug("Calculation finished, returned {} results", results.size());
        return results;
    }
}
