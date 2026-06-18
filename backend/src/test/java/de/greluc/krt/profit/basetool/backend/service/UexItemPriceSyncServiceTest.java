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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.greluc.krt.profit.basetool.backend.config.UexProperties;
import de.greluc.krt.profit.basetool.backend.dto.uex.UexItemPriceDto;
import de.greluc.krt.profit.basetool.backend.integration.UexClient;
import de.greluc.krt.profit.basetool.backend.model.GameItem;
import de.greluc.krt.profit.basetool.backend.model.GameItemPrice;
import de.greluc.krt.profit.basetool.backend.model.Terminal;
import de.greluc.krt.profit.basetool.backend.repository.GameItemPriceRepository;
import de.greluc.krt.profit.basetool.backend.repository.GameItemRepository;
import de.greluc.krt.profit.basetool.backend.repository.TerminalRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link UexItemPriceSyncService} — the R7 UEX item-price matrix sync
 * (SC_WIKI_SYNC_PLAN.md §6.7 / §11 R7). Covers the flag gate, empty-feed abort, the
 * upsert-by-(item,terminal) path, skipping unknown items / terminals, and the non-empty-seen gate
 * on the stale-row sweep.
 */
@ExtendWith(MockitoExtension.class)
class UexItemPriceSyncServiceTest {

  @Mock private UexClient uexClient;
  @Mock private GameItemRepository gameItemRepository;
  @Mock private GameItemPriceRepository gameItemPriceRepository;
  @Mock private TerminalRepository terminalRepository;
  @Mock private EntityManager entityManager;

  private UexProperties properties;
  private UexItemPriceSyncService service;

  @BeforeEach
  void setUp() {
    properties = new UexProperties();
    properties.setItemPriceSyncEnabled(true);
    service =
        new UexItemPriceSyncService(
            uexClient,
            properties,
            gameItemRepository,
            gameItemPriceRepository,
            terminalRepository,
            entityManager);
  }

  @Test
  void syncItemPrices_isNoOp_whenFlagOff() {
    properties.setItemPriceSyncEnabled(false);

    service.syncItemPrices();

    verifyNoInteractions(
        uexClient, gameItemRepository, gameItemPriceRepository, terminalRepository);
  }

  @Test
  void syncItemPrices_abortsWithoutSweep_whenFeedEmpty() {
    when(uexClient.getItemPrices()).thenReturn(List.of());

    service.syncItemPrices();

    verify(gameItemPriceRepository, never()).save(any());
    verify(gameItemPriceRepository, never()).clearStalePrices(any());
  }

  @Test
  void upsertsNewPrice_forKnownItemAndTerminal_thenRunsSweep() {
    GameItem item = gameItem();
    Terminal terminal = terminal();
    when(uexClient.getItemPrices()).thenReturn(List.of(dto(1, 107, 15461.0, 0.0, 1778763945L)));
    when(gameItemRepository.findByUexItemId(1)).thenReturn(Optional.of(item));
    when(terminalRepository.findByIdTerminal(107)).thenReturn(Optional.of(terminal));
    when(gameItemPriceRepository.findByGameItemIdAndTerminalId(item.getId(), terminal.getId()))
        .thenReturn(Optional.empty());
    stubSaveAssigningId();

    service.syncItemPrices();

    ArgumentCaptor<GameItemPrice> saved = ArgumentCaptor.forClass(GameItemPrice.class);
    verify(gameItemPriceRepository).save(saved.capture());
    GameItemPrice price = saved.getValue();
    assertSame(item, price.getGameItem());
    assertSame(terminal, price.getTerminal());
    assertEquals(15461.0, price.getPriceBuy());
    assertEquals(0.0, price.getPriceSell());
    assertEquals(1778763945L, price.getDateModified());
    assertNotNull(price.getUexSyncedAt());
    // Reserved-null columns the feed does not carry.
    assertNull(price.getPriceRent());
    assertNull(price.getStatusBuy());
    // A processed row → non-empty seen set → the stale-row sweep runs.
    verify(gameItemPriceRepository).clearStalePrices(any());
  }

  @Test
  void refreshesExistingPrice_inPlace() {
    GameItem item = gameItem();
    Terminal terminal = terminal();
    GameItemPrice existing = new GameItemPrice();
    existing.setId(UUID.randomUUID());
    existing.setGameItem(item);
    existing.setTerminal(terminal);
    existing.setPriceBuy(99.0);
    when(uexClient.getItemPrices()).thenReturn(List.of(dto(1, 107, 250.0, 300.0, 10L)));
    when(gameItemRepository.findByUexItemId(1)).thenReturn(Optional.of(item));
    when(terminalRepository.findByIdTerminal(107)).thenReturn(Optional.of(terminal));
    when(gameItemPriceRepository.findByGameItemIdAndTerminalId(item.getId(), terminal.getId()))
        .thenReturn(Optional.of(existing));
    stubSaveAssigningId();

    service.syncItemPrices();

    ArgumentCaptor<GameItemPrice> saved = ArgumentCaptor.forClass(GameItemPrice.class);
    verify(gameItemPriceRepository).save(saved.capture());
    assertSame(existing, saved.getValue(), "must update the existing row, not insert a new one");
    assertEquals(250.0, existing.getPriceBuy());
    assertEquals(300.0, existing.getPriceSell());
  }

  @Test
  void skipsUnknownItem_andDoesNotSweep_whenNothingProcessed() {
    when(uexClient.getItemPrices()).thenReturn(List.of(dto(999, 107, 1.0, 2.0, 1L)));
    when(gameItemRepository.findByUexItemId(999)).thenReturn(Optional.empty());

    service.syncItemPrices();

    verify(gameItemPriceRepository, never()).save(any());
    verify(gameItemPriceRepository, never()).clearStalePrices(any());
  }

  @Test
  void skipsUnknownTerminal() {
    GameItem item = gameItem();
    when(uexClient.getItemPrices()).thenReturn(List.of(dto(1, 555, 1.0, 2.0, 1L)));
    when(gameItemRepository.findByUexItemId(1)).thenReturn(Optional.of(item));
    when(terminalRepository.findByIdTerminal(555)).thenReturn(Optional.empty());

    service.syncItemPrices();

    verify(gameItemPriceRepository, never()).save(any());
    verify(gameItemPriceRepository, never()).clearStalePrices(any());
  }

  @Test
  void flushAndClearIfBatchFull_flushesAndClearsAtTheBatchBoundary() {
    service.flushAndClearIfBatchFull(UexItemPriceSyncService.FLUSH_BATCH_SIZE);

    verify(entityManager).flush();
    verify(entityManager).clear();
  }

  @Test
  void flushAndClearIfBatchFull_doesNothingBetweenBoundaries() {
    service.flushAndClearIfBatchFull(0);
    service.flushAndClearIfBatchFull(UexItemPriceSyncService.FLUSH_BATCH_SIZE - 1);
    service.flushAndClearIfBatchFull(UexItemPriceSyncService.FLUSH_BATCH_SIZE + 1);

    verifyNoInteractions(entityManager);
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private void stubSaveAssigningId() {
    when(gameItemPriceRepository.save(any(GameItemPrice.class)))
        .thenAnswer(
            inv -> {
              GameItemPrice p = inv.getArgument(0);
              if (p.getId() == null) {
                p.setId(UUID.randomUUID());
              }
              return p;
            });
  }

  private static UexItemPriceDto dto(
      Integer idItem, Integer idTerminal, Double buy, Double sell, Long dateModified) {
    return new UexItemPriceDto(idItem, idTerminal, buy, sell, dateModified);
  }

  private static GameItem gameItem() {
    GameItem g = new GameItem();
    g.setId(UUID.randomUUID());
    return g;
  }

  private static Terminal terminal() {
    Terminal t = new Terminal();
    t.setId(UUID.randomUUID());
    return t;
  }
}
