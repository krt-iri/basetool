package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexVehicleDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.repository.ManufacturerRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UexVehicleService {

    private final UexClient uexClient;
    private final ShipTypeRepository shipTypeRepository;
    private final ManufacturerRepository manufacturerRepository;

    @Transactional
    public void syncVehicles() {
        log.info("Starting synchronization of UEX vehicles (ships)...");
        List<UexVehicleDto> vehicles = uexClient.getVehicles();

        if (vehicles.isEmpty()) {
            log.warn("No vehicles received from UEX API. Aborting synchronization.");
            return;
        }

        int added = 0;
        int updated = 0;

        for (UexVehicleDto dto : vehicles) {
            boolean isNew = processSingleVehicle(dto);
            if (isNew) {
                added++;
            } else {
                updated++;
            }
        }

        log.info("Finished UEX vehicle sync: {} added, {} updated", added, updated);
    }

    private boolean processSingleVehicle(UexVehicleDto dto) {
        if (!StringUtils.hasText(dto.name())) {
            log.debug("Skipping vehicle with missing name: {}", dto);
            return false;
        }

        Manufacturer manufacturer = null;
        if (StringUtils.hasText(dto.companyName())) {
            Optional<Manufacturer> manOpt = manufacturerRepository.findByNameIgnoreCase(dto.companyName());
            if (manOpt.isPresent()) {
                manufacturer = manOpt.orElseThrow();
            } else {
                log.debug("Manufacturer '{}' not found for vehicle '{}'", dto.companyName(), dto.name());
            }
        }

        Optional<ShipType> existingOpt = shipTypeRepository.findByNameIgnoreCase(dto.name());

        if (existingOpt.isPresent()) {
            ShipType existing = existingOpt.orElseThrow();
            updateShipType(existing, dto, manufacturer);
            shipTypeRepository.save(existing);
            return false;
        } else {
            ShipType newShipType = new ShipType();
            newShipType.setName(dto.name());
            updateShipType(newShipType, dto, manufacturer);
            shipTypeRepository.save(newShipType);
            return true;
        }
    }

    private void updateShipType(ShipType shipType, UexVehicleDto dto, Manufacturer manufacturer) {
        if (manufacturer != null) {
            shipType.setManufacturer(manufacturer);
        }

        shipType.setScu(dto.scu());

        StringBuilder description = new StringBuilder();
        if (StringUtils.hasText(dto.nameFull())) {
            description.append("Full Name: ").append(dto.nameFull()).append("\n");
        }
        if (dto.scu() != null) {
            description.append("SCU: ").append(dto.scu()).append("\n");
        }
        if (StringUtils.hasText(dto.crew())) {
            description.append("Crew: ").append(dto.crew()).append("\n");
        }
        if (StringUtils.hasText(dto.urlWiki())) {
            description.append("Wiki: ").append(dto.urlWiki()).append("\n");
        } else if (StringUtils.hasText(dto.urlStore())) {
            description.append("Store: ").append(dto.urlStore()).append("\n");
        }

        if (!description.isEmpty()) {
            shipType.setDescription(description.toString().trim());
        }
    }
}
