package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Inbound JSON record for UEX Corp's item-price endpoint ({@code /items_prices_all}), mapped onto
 * {@code game_item_price} rows by the R7 {@code UexItemPriceSyncService}; downstream code consumes
 * the entity, not this DTO.
 *
 * <p>Binds only the fields the live feed (game 4.8.0) carries — {@code id_item}, {@code
 * id_terminal}, {@code price_buy}, {@code price_sell}, {@code date_modified}. The endpoint also
 * returns {@code id} / {@code id_category} / {@code date_added} / {@code item_name} / {@code
 * item_uuid} / {@code terminal_name}, which the lenient UEX codec mapper ignores. There is no rent
 * or buy/sell-status field in this feed (see the reserved-null columns on {@code GameItemPrice}).
 *
 * @param idItem UEX integer item id (joins to {@code game_item.uex_item_id})
 * @param idTerminal UEX integer terminal id (joins to {@code terminal.id_terminal})
 * @param priceBuy buy price in credits, or {@code null}
 * @param priceSell sell price in credits, or {@code null}
 * @param dateModified UEX unix timestamp (seconds) of the last price change, or {@code null}
 */
@Builder
public record UexItemPriceDto(
    @JsonProperty("id_item") Integer idItem,
    @JsonProperty("id_terminal") Integer idTerminal,
    @JsonProperty("price_buy") Double priceBuy,
    @JsonProperty("price_sell") Double priceSell,
    @JsonProperty("date_modified") Long dateModified) {}
