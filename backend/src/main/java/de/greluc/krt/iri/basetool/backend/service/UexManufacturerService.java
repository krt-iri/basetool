package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexCompanyDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.repository.ManufacturerRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Imports the UEX company catalog filtered to vehicle manufacturers.
 *
 * <p>UEX's {@code /companies} endpoint covers every in-universe company; this service only persists
 * rows where {@code isVehicleManufacturerFlag = true}, since the local {@code manufacturer} table
 * is exclusively used for ship-type manufacturer references. Match is by case-insensitive name so
 * the same manufacturer never gets duplicated even if UEX changes its canonical capitalization.
 * Empty UEX response short-circuits without wiping local data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UexManufacturerService {

  private final UexClient uexClient;
  private final ManufacturerRepository manufacturerRepository;

  /**
   * Pulls the company catalog and upserts the vehicle-manufacturer subset. Single transaction for
   * the entire sweep; counters are emitted as INFO-level summary at the end.
   */
  @Transactional
  public void syncManufacturers() {
    log.info("Starting synchronization of UEX manufacturers...");
    List<UexCompanyDto> companies = uexClient.getCompanies();

    if (companies.isEmpty()) {
      log.warn("No companies received from UEX API. Aborting synchronization.");
      return;
    }

    int added = 0;
    int updated = 0;

    for (UexCompanyDto dto : companies) {
      if (Boolean.TRUE.equals(dto.isVehicleManufacturerFlag())) {
        boolean isNew = processSingleCompany(dto);
        if (isNew) {
          added++;
        } else {
          updated++;
        }
      }
    }

    log.info("Finished UEX manufacturer sync: {} added, {} updated", added, updated);
  }

  private boolean processSingleCompany(UexCompanyDto dto) {
    if (!StringUtils.hasText(dto.name())) {
      log.debug("Skipping company with missing name: {}", dto);
      return false;
    }

    Optional<Manufacturer> existingOpt = manufacturerRepository.findByNameIgnoreCase(dto.name());

    if (existingOpt.isPresent()) {
      Manufacturer existing = existingOpt.orElseThrow();
      updateManufacturer(existing, dto);
      manufacturerRepository.save(existing);
      return false;
    } else {
      Manufacturer newManufacturer = new Manufacturer();
      newManufacturer.setName(dto.name());
      updateManufacturer(newManufacturer, dto);
      manufacturerRepository.save(newManufacturer);
      return true;
    }
  }

  private void updateManufacturer(Manufacturer manufacturer, UexCompanyDto dto) {
    String abbr = StringUtils.hasText(dto.nickname()) ? dto.nickname() : dto.name();
    // Fallback for abbreviation if it's too long or if we want to ensure some consistency?
    // Let's just use what UEX provides.
    manufacturer.setAbbreviation(abbr);

    if (StringUtils.hasText(dto.nickname())) {
      manufacturer.setNickname(dto.nickname());
    }

    if (StringUtils.hasText(dto.wiki())) {
      manufacturer.setWiki(dto.wiki());
    }

    StringBuilder description = new StringBuilder();
    if (StringUtils.hasText(dto.industry())) {
      description.append("Industry: ").append(dto.industry()).append("\n");
    }
    if (StringUtils.hasText(dto.wiki())) {
      description.append("Wiki: ").append(dto.wiki());
    }
    if (!description.isEmpty()) {
      manufacturer.setDescription(description.toString().trim());
    }
  }
}
