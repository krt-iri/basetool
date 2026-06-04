/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
 * <p>Match chain: {@code byUexCompanyId(dto.id)} → {@code byNameIgnoreCase(dto.name)} → {@code
 * byAbbreviationIgnoreCase(nickname)}. The abbreviation step is load-bearing, not cosmetic: {@code
 * abbreviation} is UNIQUE, and the vehicle-manufacturer rows seeded before this sync persisted
 * every company store the short nickname as their {@code name} — local {@code "Esperia"} vs UEX
 * {@code name="Esperia Incorporation"}, {@code nickname="Esperia"}. Such a row misses the id and
 * name lookups, so without the abbreviation fallback the insert collides on {@code
 * manufacturer_abbreviation_key}. Probing all three unique keys before insert also guarantees no
 * upsert can violate a unique constraint and poison the single-transaction sweep. Once a row's
 * {@code uex_company_id} is populated the fallbacks never fire on subsequent syncs.
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
   * Upserts a single UEX company DTO via the {@code uexCompanyId → name → abbreviation} match
   * chain, adopting a pre-existing row (e.g. a legacy short-named vehicle manufacturer) instead of
   * inserting a duplicate that would violate the UNIQUE {@code abbreviation} and abort the whole
   * sweep. {@code true} return = a new row was inserted.
   *
   * @param dto inbound UEX row
   * @param now timestamp to stamp on the row
   * @return {@code true} when the row was newly inserted
   */
  private boolean upsertCompany(UexCompanyDto dto, Instant now) {
    String abbreviation = StringUtils.hasText(dto.nickname()) ? dto.nickname() : dto.name();

    Optional<Manufacturer> existingOpt = Optional.empty();
    if (dto.id() != null) {
      existingOpt = manufacturerRepository.findByUexCompanyId(dto.id());
    }
    if (existingOpt.isEmpty()) {
      existingOpt = manufacturerRepository.findByNameIgnoreCase(dto.name());
    }
    if (existingOpt.isEmpty()) {
      // The UNIQUE key is abbreviation, so a miss on both id and name still has to reconcile here
      // before inserting — otherwise a legacy row whose name is the short nickname (Esperia,
      // Banu, Vanduul) collides on manufacturer_abbreviation_key.
      existingOpt = manufacturerRepository.findByAbbreviationIgnoreCase(abbreviation);
    }

    final boolean isNew = existingOpt.isEmpty();
    Manufacturer manufacturer = existingOpt.orElseGet(Manufacturer::new);

    manufacturer.setName(dto.name());
    manufacturer.setAbbreviation(abbreviation);
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
