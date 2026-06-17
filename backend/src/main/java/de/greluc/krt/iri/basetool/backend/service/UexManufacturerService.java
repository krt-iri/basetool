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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
 * byAbbreviation(nickname) on an unclaimed row}. The abbreviation step is load-bearing, not
 * cosmetic: the vehicle-manufacturer rows seeded before this sync persisted every company store the
 * short nickname as their {@code name} — local {@code "Esperia"} vs UEX {@code name="Esperia
 * Incorporation"}, {@code nickname="Esperia"}. Such a legacy row misses the id and name lookups, so
 * without the abbreviation fallback it would be left orphaned and a parallel row inserted. The
 * fallback is scoped to <em>unclaimed</em> rows ({@code uex_company_id IS NULL}) so it adopts only
 * the legacy seed and never hijacks a row that already belongs to another UEX company that happens
 * to share the nickname. Once a row's {@code uex_company_id} is populated the fallback never fires
 * for it again.
 *
 * <p><strong>Per-company isolation.</strong> Each company is upserted in its own {@code
 * REQUIRES_NEW} transaction via {@link #upsertCompanyWithinTransaction(UexCompanyDto, Instant)},
 * invoked through the {@link #self} proxy (a direct {@code this} call would be self-invocation and
 * silently skip the new transaction — the CLAUDE.md self-invocation trap). The sweep used to run as
 * one transaction, so a single row that violated a unique constraint (notably the now-dropped
 * {@code abbreviation} UNIQUE — two distinct UEX companies sharing the nickname {@code "Esperia"})
 * marked the whole transaction rollback-only and discarded every other manufacturer update for the
 * day, surfacing only at commit as an {@code UnexpectedRollbackException} that also aborted the
 * rest of the UEX sweep. With per-company transactions a bad row rolls back only itself; the {@code
 * catch} in the loop counts it as skipped and the remaining companies still commit (REQ-DATA-004).
 *
 * <p>Empty UEX response short-circuits without wiping local data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UexManufacturerService {

  private final UexClient uexClient;
  private final ManufacturerRepository manufacturerRepository;

  /**
   * Self-reference, resolved lazily so each per-company upsert runs through the Spring transaction
   * proxy. Calling {@link #upsertCompanyWithinTransaction(UexCompanyDto, Instant)} via {@code this}
   * would be self-invocation and run in the caller's (here absent) transaction instead of opening
   * the {@code REQUIRES_NEW} one, defeating the per-company isolation.
   */
  private final ObjectProvider<UexManufacturerService> self;

  /**
   * Pulls the company catalogue and upserts every row, each in its own {@code REQUIRES_NEW}
   * transaction so one constraint violation cannot roll back the whole sweep. Deliberately not
   * {@code @Transactional}: the HTTP fetch and the loop hold no transaction, and the only writes
   * happen inside the per-company nested transactions. Counter summary at INFO when done.
   */
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
        boolean isNew = self.getObject().upsertCompanyWithinTransaction(dto, now);
        if (isNew) {
          added++;
        } else {
          updated++;
        }
      } catch (Exception e) {
        // The failed company's own REQUIRES_NEW transaction has rolled back; the outer sweep is
        // untouched, so we just tally it and carry on with the next company.
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
   * Upserts a single UEX company DTO in its own {@code REQUIRES_NEW} transaction via the {@code
   * uexCompanyId → name → unclaimed-abbreviation} match chain, adopting a pre-existing row (e.g. a
   * legacy short-named vehicle manufacturer) instead of inserting a parallel one. Running in a
   * dedicated nested transaction means a failure here (e.g. a residual unique-constraint clash)
   * rolls back only this company and never poisons the rest of the sweep; the caller's loop catches
   * it and continues. Must be invoked through the {@link #self} proxy — a direct {@code this} call
   * would be self-invocation and skip the new transaction.
   *
   * @param dto inbound UEX row
   * @param now timestamp to stamp on the row
   * @return {@code true} when the row was newly inserted
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean upsertCompanyWithinTransaction(UexCompanyDto dto, Instant now) {
    String abbreviation = StringUtils.hasText(dto.nickname()) ? dto.nickname() : dto.name();

    Optional<Manufacturer> existingOpt = Optional.empty();
    if (dto.id() != null) {
      existingOpt = manufacturerRepository.findByUexCompanyId(dto.id());
    }
    if (existingOpt.isEmpty()) {
      existingOpt = manufacturerRepository.findByNameIgnoreCase(dto.name());
    }
    if (existingOpt.isEmpty()) {
      // Adopt a legacy row whose name is the short nickname (Esperia, Banu, Vanduul) so we don't
      // strand it and insert a parallel row. Scoped to unclaimed rows (uex_company_id IS NULL): a
      // row already stamped by another UEX company that shares this nickname must keep its own
      // identity — two such companies then each get their own row (abbreviation is no longer
      // UNIQUE, V158 / REQ-DATA-004), instead of fighting over one.
      existingOpt =
          manufacturerRepository.findOldestUnclaimedByAbbreviationIgnoreCase(abbreviation);
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
