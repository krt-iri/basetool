package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexCompanyDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.repository.ManufacturerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Imports UEX Corp's {@code /companies} catalogue into the local {@code manufacturer} table.
 *
 * <p>R2 expansion: previously only persisted vehicle manufacturers (the manufacturer table served
 * the ship picker exclusively). R2 ships the UEX item catalogue, so item manufacturers are also
 * needed — every company UEX returns now gets a {@code manufacturer} row, with the new {@code
 * is_item_manufacturer} / {@code is_vehicle_manufacturer} flags telling consumers which surface(s)
 * it serves.
 *
 * <p>Match chain (R2): {@code byUexCompanyId(dto.id)} → {@code byNameIgnoreCase} fallback for the
 * legacy rows created before V107 added the {@code uex_company_id} column. Once a row's {@code
 * uex_company_id} is populated the fallback never fires on subsequent syncs.
 *
 * <p>Empty UEX response short-circuits without wiping local data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UexManufacturerService {

  private final UexClient uexClient;
  private final ManufacturerRepository manufacturerRepository;

  /**
   * Pulls the company catalogue and upserts every row. Single transaction for the full sweep;
   * counter summary at INFO when done.
   */
  @Transactional
  public void syncManufacturers() {
    log.info("Starting synchronization of UEX manufacturers...");
    List<UexCompanyDto> companies = uexClient.getCompanies();
    if (companies.isEmpty()) {
      log.warn("No companies received from UEX API. Aborting synchronization.");
      return;
    }

    Instant now = Instant.now();
    int added = 0;
    int updated = 0;
    int skipped = 0;
    for (UexCompanyDto dto : companies) {
      if (!StringUtils.hasText(dto.name())) {
        log.debug("Skipping company with missing name: {}", dto);
        skipped++;
        continue;
      }
      try {
        boolean isNew = upsertCompany(dto, now);
        if (isNew) {
          added++;
        } else {
          updated++;
        }
      } catch (Exception e) {
        log.error("Failed to process UEX company dto: {}", dto, e);
        skipped++;
      }
    }
    log.info(
        "Finished UEX manufacturer sync: {} added, {} updated, {} skipped",
        added,
        updated,
        skipped);
  }

  /**
   * Upserts a single UEX company DTO. {@code true} return = a new row was inserted.
   *
   * @param dto inbound UEX row
   * @param now timestamp to stamp on the row
   * @return {@code true} when the row was newly inserted
   */
  private boolean upsertCompany(UexCompanyDto dto, Instant now) {
    Optional<Manufacturer> existingOpt = Optional.empty();
    if (dto.id() != null) {
      existingOpt = manufacturerRepository.findByUexCompanyId(dto.id());
    }
    if (existingOpt.isEmpty()) {
      existingOpt = manufacturerRepository.findByNameIgnoreCase(dto.name());
    }

    final boolean isNew = existingOpt.isEmpty();
    Manufacturer manufacturer = existingOpt.orElseGet(Manufacturer::new);

    manufacturer.setName(dto.name());
    String abbr = StringUtils.hasText(dto.nickname()) ? dto.nickname() : dto.name();
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

    // R2 cross-ref column writes
    manufacturer.setUexCompanyId(dto.id());
    manufacturer.setIndustry(dto.industry());
    manufacturer.setIsItemManufacturer(asBoolean(dto.isItemManufacturer()));
    manufacturer.setIsVehicleManufacturer(asBoolean(dto.isVehicleManufacturer()));
    manufacturer.setUexSyncedAt(now);
    manufacturer.setUexDeletedAt(null);

    manufacturerRepository.save(manufacturer);
    return isNew;
  }

  /**
   * Normalises UEX's 0/1 integer flag into a {@link Boolean}.
   *
   * @param flag UEX-style integer
   * @return {@code true} when {@code flag == 1}, {@code false} otherwise (including {@code null})
   */
  private static Boolean asBoolean(Integer flag) {
    return flag != null && flag == 1;
  }
}
