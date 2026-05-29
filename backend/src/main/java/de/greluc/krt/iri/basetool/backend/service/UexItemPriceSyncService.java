package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.config.UexProperties;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexItemPriceDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.GameItemPrice;
import de.greluc.krt.iri.basetool.backend.model.Terminal;
import de.greluc.krt.iri.basetool.backend.repository.GameItemPriceRepository;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.TerminalRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * R7 UEX item-price sync (SC_WIKI_SYNC_PLAN.md §8.x / §11 R7). Walks the full UEX item-price matrix
 * ({@code /items_prices_all}) and upserts one {@code game_item_price} row per (item, terminal)
 * pair.
 *
 * <p>Resolution: {@code id_item → game_item} via {@link GameItemRepository#findByUexItemId} and
 * {@code id_terminal → terminal} via {@link TerminalRepository#findByIdTerminal}. Unlike the
 * commodity-price sync, an unknown item is <b>skipped</b> (not auto-created): {@code game_item} is
 * owned by the UEX item catalogue + Wiki backfill, which run earlier in the same scheduler tick, so
 * a price referencing an item not yet catalogued resolves on the next cycle. Unknown terminals are
 * skipped too (the universe sync owns {@code terminal}).
 *
 * <p>After the loop the ids of every touched row are passed to {@link
 * GameItemPriceRepository#clearStalePrices} to null out (item, terminal) pairs UEX no longer
 * returns. The sweep is gated on a non-empty touched-set so a run that fails on every row never
 * wipes the whole matrix; an empty upstream response short-circuits before the sweep entirely.
 *
 * <p>Gated behind {@code krt.uex.item-price-sync-enabled} (default {@code false}); ships dark. The
 * payload is the largest UEX feed (~24 000 rows) so this is the most expensive UEX sync — flip it
 * on deliberately, per the deployment runbook §7.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UexItemPriceSyncService {

  private final UexClient uexClient;
  private final UexProperties uexProperties;
  private final GameItemRepository gameItemRepository;
  private final GameItemPriceRepository gameItemPriceRepository;
  private final TerminalRepository terminalRepository;

  /**
   * Runs the full item-price matrix sync. No-op (with an INFO line) when the feature flag is off;
   * an empty UEX response short-circuits before the stale-row sweep.
   */
  @Transactional
  public void syncItemPrices() {
    if (!Boolean.TRUE.equals(uexProperties.getItemPriceSyncEnabled())) {
      log.info(
          "UEX item-price sync invoked but disabled (krt.uex.item-price-sync-enabled=false) —"
              + " skipping.");
      return;
    }

    log.info("Starting synchronization of UEX item prices...");
    List<UexItemPriceDto> dtos = uexClient.getItemPrices();
    if (dtos.isEmpty()) {
      log.warn("No item prices received from UEX API. Aborting synchronization (no stale sweep).");
      return;
    }

    Set<UUID> seenPriceIds = new HashSet<>();
    Instant now = Instant.now();
    int processed = 0;
    int skipped = 0;
    for (UexItemPriceDto dto : dtos) {
      try {
        UUID savedId = upsert(dto, now);
        if (savedId != null) {
          seenPriceIds.add(savedId);
          processed++;
        } else {
          skipped++;
        }
      } catch (Exception e) {
        log.error("Failed to process UEX item-price dto: {}", dto, e);
      }
    }

    if (seenPriceIds.isEmpty()) {
      log.warn(
          "Skipping stale-row cleanup because no item-price row could be processed "
              + "({} dto(s) received). Refusing to wipe the entire item-price matrix.",
          dtos.size());
    } else {
      int cleared = gameItemPriceRepository.clearStalePrices(seenPriceIds);
      if (cleared > 0) {
        log.info("Cleared prices on {} game_item_price row(s) no longer returned by UEX.", cleared);
      }
    }
    log.info(
        "Finished UEX item-price sync: {} processed, {} skipped (unknown item / terminal).",
        processed,
        skipped);
  }

  /**
   * Upserts one item-price DTO into {@code game_item_price}. Returns the saved row id, or {@code
   * null} when the row is skipped — missing ids, an item not yet in {@code game_item}, or a
   * terminal not yet in {@code terminal}.
   *
   * @param dto inbound UEX item-price row
   * @param now timestamp to stamp on the row
   * @return the saved {@link GameItemPrice} id, or {@code null} if skipped
   */
  private UUID upsert(UexItemPriceDto dto, Instant now) {
    if (dto.idItem() == null || dto.idTerminal() == null) {
      return null;
    }
    GameItem item = gameItemRepository.findByUexItemId(dto.idItem()).orElse(null);
    if (item == null) {
      return null;
    }
    Terminal terminal = terminalRepository.findByIdTerminal(dto.idTerminal()).orElse(null);
    if (terminal == null) {
      return null;
    }

    GameItemPrice price =
        gameItemPriceRepository
            .findByGameItemIdAndTerminalId(item.getId(), terminal.getId())
            .orElseGet(
                () -> {
                  GameItemPrice fresh = new GameItemPrice();
                  fresh.setGameItem(item);
                  fresh.setTerminal(terminal);
                  return fresh;
                });

    price.setPriceBuy(dto.priceBuy());
    price.setPriceSell(dto.priceSell());
    price.setDateModified(dto.dateModified());
    price.setUexSyncedAt(now);
    return gameItemPriceRepository.save(price).getId();
  }
}
