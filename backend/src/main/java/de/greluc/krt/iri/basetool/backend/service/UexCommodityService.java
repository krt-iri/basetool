package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexCommodityDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexCommodityPriceDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialPrice;
import de.greluc.krt.iri.basetool.backend.model.MaterialType;
import de.greluc.krt.iri.basetool.backend.model.Terminal;
import de.greluc.krt.iri.basetool.backend.repository.MaterialPriceRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.TerminalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UexCommodityService {

    private final UexClient uexClient;
    private final MaterialRepository materialRepository;
    private final MaterialPriceRepository materialPriceRepository;
    private final TerminalRepository terminalRepository;

    @Transactional
    public void fetchAndProcessCommoditiesPrices() {
        log.info("Starting synchronization of UEX commodities...");
        List<UexCommodityDto> commodities = uexClient.getCommodities();
        
        if (!commodities.isEmpty()) {
            int commoditiesProcessed = 0;
            for (UexCommodityDto dto : commodities) {
                try {
                    if (dto.id() != null && dto.name() != null) {
                        Material material = materialRepository.findByIdCommodity(dto.id())
                                .orElseGet(() -> materialRepository.findByName(dto.name())
                                        .map(m -> {
                                            m.setIdCommodity(dto.id());
                                            return m;
                                        })
                                        .orElseGet(() -> {
                                            Material newMaterial = new Material();
                                            newMaterial.setIdCommodity(dto.id());
                                            newMaterial.setName(dto.name());
                                            return newMaterial;
                                        }));

                        material.setType(determineMaterialType(dto));
                        material.setCode(dto.code());
                        material.setSlug(dto.slug());
                        material.setKind(dto.kind());
                        material.setWeightScu(dto.weightScu());
                        material.setPriceBuy(dto.priceBuy());
                        material.setPriceSell(dto.priceSell());
                        material.setIsAvailable(dto.isAvailable());
                        material.setIsAvailableLive(dto.isAvailableLive());
                        material.setIsExtractable(dto.isExtractable());
                        material.setIsMineral(dto.isMineral());
                        material.setIsRaw(dto.isRaw());
                        material.setIsPure(dto.isPure());
                        material.setIsRefined(dto.isRefined());
                        material.setIsRefinable(dto.isRefinable());
                        material.setIsHarvestable(dto.isHarvestable());
                        material.setIsBuyable(dto.isBuyable());
                        material.setIsSellable(dto.isSellable());
                        material.setIsTemporary(dto.isTemporary());
                        material.setIsIllegal(dto.isIllegal());
                        material.setIsVolatileQt(dto.isVolatileQt());
                        material.setIsVolatileTime(dto.isVolatileTime());
                        material.setIsInert(dto.isInert());
                        material.setIsExplosive(dto.isExplosive());
                        material.setIsBuggy(dto.isBuggy());
                        material.setIsFuel(dto.isFuel());
                        materialRepository.save(material);

                        commoditiesProcessed++;
                    }
                } catch (Exception e) {
                    log.error("Failed to process commodity dto: {}", dto, e);
                }
            }
            log.info("Finished commodities synchronization. Processed {} items.", commoditiesProcessed);
        } else {
            log.warn("No commodities received from UEX API.");
        }

        log.info("Starting synchronization of UEX commodity prices...");
        List<UexCommodityPriceDto> dtos = uexClient.getCommoditiesPricesAll();
        
        if (dtos.isEmpty()) {
            log.warn("No data received from UEX API. Aborting synchronization.");
            return;
        }

        int processed = 0;
        for (UexCommodityPriceDto dto : dtos) {
            try {
                processSingleDto(dto);
                processed++;
            } catch (Exception e) {
                log.error("Failed to process commodity dto: {}", dto, e);
            }
        }
        
        log.info("Finished synchronization. Processed {} items.", processed);
    }

    private void processSingleDto(UexCommodityPriceDto dto) {
        if (dto.idCommodity() == null || dto.idTerminal() == null) {
            log.warn("Missing commodity or terminal ID in DTO: {}", dto);
            return;
        }

        Material material = resolveOrCreateMaterial(dto);
        Optional<Terminal> terminalOpt = terminalRepository.findByIdTerminal(dto.idTerminal());

        if (terminalOpt.isEmpty()) {
            return;
        }
        Terminal terminal = terminalOpt.orElseThrow();

        Optional<MaterialPrice> priceOpt = materialPriceRepository.findByMaterialIdAndTerminalId(material.getId(), terminal.getId());
        MaterialPrice price = priceOpt.orElseGet(() -> {
            MaterialPrice newPrice = new MaterialPrice();
            newPrice.setMaterial(material);
            newPrice.setTerminal(terminal);
            return newPrice;
        });

        price.setPriceBuy(dto.priceBuy());
        price.setPriceSell(dto.priceSell());
        price.setScuBuy(dto.scuBuy());
        price.setScuSell(dto.scuSell());
        price.setScuSellStock(dto.scuSellStock());
        price.setStatusBuy(dto.isStatusBuy());
        price.setStatusSell(dto.isStatusSell());
        price.setDateModified(dto.getParsedDateModified());

        materialPriceRepository.save(price);
    }

    private Material resolveOrCreateMaterial(UexCommodityPriceDto dto) {
        return materialRepository.findByIdCommodity(dto.idCommodity())
                .orElseGet(() -> materialRepository.findByName(dto.commodityName())
                        .map(m -> {
                            m.setIdCommodity(dto.idCommodity());
                            return materialRepository.save(m);
                        })
                        .orElseGet(() -> {
                            Material newMaterial = new Material();
                            newMaterial.setIdCommodity(dto.idCommodity());
                            newMaterial.setName(dto.commodityName() != null ? dto.commodityName() : "Unknown-" + dto.idCommodity());
                            newMaterial.setType(MaterialType.NO_REFINE); // Default type
                            return materialRepository.save(newMaterial);
                        }));
    }

    private MaterialType determineMaterialType(UexCommodityDto dto) {
        if (Integer.valueOf(1).equals(dto.isRefined())) {
            return MaterialType.REFINED;
        } else if (Integer.valueOf(1).equals(dto.isRefinable())) {
            return MaterialType.RAW;
        }
        return MaterialType.NO_REFINE;
    }
}
