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

package de.greluc.krt.profit.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.greluc.krt.profit.basetool.backend.dto.uex.UexCompanyDto;
import de.greluc.krt.profit.basetool.backend.integration.UexClient;
import de.greluc.krt.profit.basetool.backend.model.Manufacturer;
import de.greluc.krt.profit.basetool.backend.repository.ManufacturerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for {@link UexManufacturerService}.
 *
 * <p>R2 expansion: the service now persists item manufacturers alongside vehicle manufacturers (R2
 * ships the UEX item catalogue, so both surfaces need rows). Matching is by UEX company id first,
 * falling back to case-insensitive name for legacy rows created before V107 added the {@code
 * uex_company_id} column, then to an unclaimed abbreviation match.
 *
 * <p>Each company is upserted through the {@link #self} proxy so its write runs in a dedicated
 * {@code REQUIRES_NEW} transaction (REQ-DATA-004). The proxy is stubbed to return the
 * service-under-test so the real upsert logic executes; the transaction boundary itself is a no-op
 * under plain Mockito, which is exactly why the per-company {@code catch} resilience is
 * unit-testable here.
 */
@ExtendWith(MockitoExtension.class)
class UexManufacturerServiceTest {

  @Mock private UexClient uexClient;

  @Mock private ManufacturerRepository manufacturerRepository;

  @Mock private ObjectProvider<UexManufacturerService> self;

  @InjectMocks private UexManufacturerService uexManufacturerService;

  @BeforeEach
  void wireSelfProxy() {
    // self.getObject() must return the real instance so the per-company REQUIRES_NEW upsert runs
    // its actual logic; lenient() because the empty-response test returns before the loop.
    lenient().when(self.getObject()).thenReturn(uexManufacturerService);
  }

  @Test
  void persistsBothVehicleAndItemManufacturers_andWritesR2CrossRefColumns() {
    UexCompanyDto vehicleDto =
        UexCompanyDto.builder()
            .id(1)
            .name("Aegis Dynamics")
            .nickname("AEGS")
            .industry("Aerospace")
            .wiki("wiki-link")
            .isVehicleManufacturer(1)
            .build();
    UexCompanyDto itemDto =
        UexCompanyDto.builder()
            .id(2)
            .name("Casaba Outlet")
            .nickname("Casaba")
            .industry("Fashion")
            .isItemManufacturer(1)
            .isVehicleManufacturer(0)
            .build();

    when(uexClient.getCompanies()).thenReturn(List.of(vehicleDto, itemDto));
    when(manufacturerRepository.findByUexCompanyId(1)).thenReturn(Optional.empty());
    when(manufacturerRepository.findByUexCompanyId(2)).thenReturn(Optional.empty());
    when(manufacturerRepository.findByNameIgnoreCase("Aegis Dynamics"))
        .thenReturn(Optional.empty());
    when(manufacturerRepository.findByNameIgnoreCase("Casaba Outlet")).thenReturn(Optional.empty());

    uexManufacturerService.syncManufacturers();

    ArgumentCaptor<Manufacturer> captor = ArgumentCaptor.forClass(Manufacturer.class);
    verify(manufacturerRepository, times(2)).save(captor.capture());

    Manufacturer aegis = captor.getAllValues().get(0);
    assertEquals("Aegis Dynamics", aegis.getName());
    assertEquals("AEGS", aegis.getAbbreviation());
    assertEquals(1, aegis.getUexCompanyId());
    assertEquals("Aerospace", aegis.getIndustry());
    assertTrue(aegis.getIsVehicleManufacturer());
    assertFalse(aegis.getIsItemManufacturer());
    assertNotNull(aegis.getUexSyncedAt());

    Manufacturer casaba = captor.getAllValues().get(1);
    assertEquals("Casaba Outlet", casaba.getName());
    assertEquals(2, casaba.getUexCompanyId());
    assertTrue(casaba.getIsItemManufacturer());
    assertFalse(casaba.getIsVehicleManufacturer());
  }

  @Test
  void matchByUexCompanyId_shortCircuitsTheNameFallback() {
    UexCompanyDto dto =
        UexCompanyDto.builder()
            .id(42)
            .name("Aegis Dynamics")
            .nickname("AEGS-New")
            .industry("Aerospace-New")
            .isVehicleManufacturer(1)
            .build();
    Manufacturer existing = new Manufacturer();
    existing.setName("Aegis Dynamics");
    existing.setAbbreviation("AEGS-Old");
    existing.setUexCompanyId(42);

    when(uexClient.getCompanies()).thenReturn(List.of(dto));
    when(manufacturerRepository.findByUexCompanyId(42)).thenReturn(Optional.of(existing));

    uexManufacturerService.syncManufacturers();

    verify(manufacturerRepository).findByUexCompanyId(42);
    verify(manufacturerRepository, never()).findByNameIgnoreCase(any());
    assertEquals("AEGS-New", existing.getAbbreviation());
    assertEquals("Aerospace-New", existing.getIndustry());
  }

  @Test
  void nameFallback_fires_whenUexCompanyIdNotYetPopulated_onLegacyRow() {
    UexCompanyDto dto =
        UexCompanyDto.builder()
            .id(42)
            .name("Aegis Dynamics")
            .nickname("AEGS")
            .industry("Aerospace")
            .isVehicleManufacturer(1)
            .build();
    Manufacturer legacy = new Manufacturer();
    legacy.setName("Aegis Dynamics");
    legacy.setAbbreviation("AEGS");
    // legacy: no uexCompanyId populated yet (pre-V107)

    when(uexClient.getCompanies()).thenReturn(List.of(dto));
    when(manufacturerRepository.findByUexCompanyId(42)).thenReturn(Optional.empty());
    when(manufacturerRepository.findByNameIgnoreCase("Aegis Dynamics"))
        .thenReturn(Optional.of(legacy));

    uexManufacturerService.syncManufacturers();

    verify(manufacturerRepository).save(legacy);
    assertEquals(42, legacy.getUexCompanyId(), "name-fallback hit must backfill uex_company_id");
  }

  @Test
  void abbreviationFallback_adoptsUnclaimedLegacyShortNamedRow_insteadOfInsertingDuplicate() {
    // UEX returns the full company name, but the legacy vehicle-manufacturer row was seeded with
    // its short nickname as the name: local "Esperia" vs UEX name "Esperia Incorporation" /
    // nickname "Esperia". Both the id and name lookups miss, so the unclaimed-abbreviation fallback
    // adopts the legacy row rather than stranding it and inserting a parallel one.
    UexCompanyDto dto =
        UexCompanyDto.builder()
            .id(278)
            .name("Esperia Incorporation")
            .nickname("Esperia")
            .industry("Aerospace")
            .isItemManufacturer(1)
            .isVehicleManufacturer(1)
            .build();
    Manufacturer legacy = new Manufacturer();
    legacy.setName("Esperia");
    legacy.setAbbreviation("Esperia");
    // legacy: seeded before this sync persisted every company, so no uexCompanyId yet (unclaimed).

    when(uexClient.getCompanies()).thenReturn(List.of(dto));
    when(manufacturerRepository.findByUexCompanyId(278)).thenReturn(Optional.empty());
    when(manufacturerRepository.findByNameIgnoreCase("Esperia Incorporation"))
        .thenReturn(Optional.empty());
    when(manufacturerRepository.findOldestUnclaimedByAbbreviationIgnoreCase("Esperia"))
        .thenReturn(Optional.of(legacy));

    uexManufacturerService.syncManufacturers();

    // Adopted the existing row: exactly one save, and it is the legacy instance — no duplicate row.
    ArgumentCaptor<Manufacturer> captor = ArgumentCaptor.forClass(Manufacturer.class);
    verify(manufacturerRepository, times(1)).save(captor.capture());
    Manufacturer saved = captor.getValue();
    assertSame(legacy, saved, "must adopt the abbreviation-matched row, not insert a new one");
    assertEquals(
        278, saved.getUexCompanyId(), "abbreviation-fallback hit must backfill uex_company_id");
    assertEquals("Esperia", saved.getAbbreviation(), "the abbreviation label is preserved");
    assertEquals(
        "Esperia Incorporation", saved.getName(), "name is updated to the UEX-canonical full name");
  }

  // covers REQ-DATA-004 — two distinct UEX companies sharing a nickname-derived abbreviation each
  // keep their own row (abbreviation is no longer UNIQUE); the second is not merged into the first.
  @Test
  void twoCompaniesSharingAbbreviation_eachGetsItsOwnRow() {
    UexCompanyDto first =
        UexCompanyDto.builder().id(278).name("Esperia Incorporation").nickname("Esperia").build();
    UexCompanyDto second =
        UexCompanyDto.builder().id(279).name("Esperia Defense Systems").nickname("Esperia").build();

    when(uexClient.getCompanies()).thenReturn(List.of(first, second));
    when(manufacturerRepository.findByUexCompanyId(278)).thenReturn(Optional.empty());
    when(manufacturerRepository.findByUexCompanyId(279)).thenReturn(Optional.empty());
    when(manufacturerRepository.findByNameIgnoreCase("Esperia Incorporation"))
        .thenReturn(Optional.empty());
    when(manufacturerRepository.findByNameIgnoreCase("Esperia Defense Systems"))
        .thenReturn(Optional.empty());
    // No unclaimed legacy row: the first company's row is claimed the moment it is stamped, so the
    // second company's unclaimed-abbreviation lookup misses and it inserts its own row.
    when(manufacturerRepository.findOldestUnclaimedByAbbreviationIgnoreCase("Esperia"))
        .thenReturn(Optional.empty());

    uexManufacturerService.syncManufacturers();

    ArgumentCaptor<Manufacturer> captor = ArgumentCaptor.forClass(Manufacturer.class);
    verify(manufacturerRepository, times(2)).save(captor.capture());
    List<Manufacturer> saved = captor.getAllValues();
    assertNotSame(saved.get(0), saved.get(1), "the two companies must not share a single row");
    assertEquals(278, saved.get(0).getUexCompanyId());
    assertEquals(279, saved.get(1).getUexCompanyId());
    assertEquals("Esperia", saved.get(0).getAbbreviation());
    assertEquals(
        "Esperia",
        saved.get(1).getAbbreviation(),
        "both keep the shared abbreviation — it is no longer a unique key");
  }

  // covers REQ-DATA-004 — a single failing company must not poison the batch: its REQUIRES_NEW
  // transaction rolls back alone and the loop continues, so the remaining companies still commit.
  @Test
  void oneCompanyUpsertFailing_doesNotAbortTheRestOfTheBatch() {
    UexCompanyDto poison =
        UexCompanyDto.builder().id(278).name("Esperia Incorporation").nickname("Esperia").build();
    UexCompanyDto healthy =
        UexCompanyDto.builder().id(1).name("Aegis Dynamics").nickname("AEGS").build();

    when(uexClient.getCompanies()).thenReturn(List.of(poison, healthy));
    when(manufacturerRepository.findByUexCompanyId(278)).thenReturn(Optional.empty());
    when(manufacturerRepository.findByUexCompanyId(1)).thenReturn(Optional.empty());
    when(manufacturerRepository.findByNameIgnoreCase("Esperia Incorporation"))
        .thenReturn(Optional.empty());
    when(manufacturerRepository.findByNameIgnoreCase("Aegis Dynamics"))
        .thenReturn(Optional.empty());
    when(manufacturerRepository.findOldestUnclaimedByAbbreviationIgnoreCase(any()))
        .thenReturn(Optional.empty());
    // First save simulates a residual constraint violation (as the per-company tx would surface);
    // the second company must still be saved.
    when(manufacturerRepository.save(any()))
        .thenThrow(new DataIntegrityViolationException("duplicate key"))
        .thenReturn(null);

    // The sweep swallows the per-company failure instead of propagating it.
    assertDoesNotThrow(() -> uexManufacturerService.syncManufacturers());

    ArgumentCaptor<Manufacturer> captor = ArgumentCaptor.forClass(Manufacturer.class);
    verify(manufacturerRepository, times(2)).save(captor.capture());
    assertEquals(
        "Aegis Dynamics",
        captor.getAllValues().get(1).getName(),
        "the healthy company after the failing one is still upserted");
  }

  @Test
  void emptyResponse_skipsWrites() {
    when(uexClient.getCompanies()).thenReturn(List.of());

    uexManufacturerService.syncManufacturers();

    verify(manufacturerRepository, never()).save(any());
  }
}
