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

package de.greluc.krt.profit.basetool.backend.dto.uex;

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
 * <p>Component {@code idItem} is the UEX integer item id (joins to {@code game_item.uex_item_id});
 * {@code idTerminal} is the UEX integer terminal id (joins to {@code terminal.id_terminal}); {@code
 * priceBuy} and {@code priceSell} are buy/sell prices in credits or {@code null}; {@code
 * dateModified} is the UEX unix timestamp (seconds) of the last price change, or {@code null}.
 */
@Builder
public record UexItemPriceDto(
    @JsonProperty("id_item") Integer idItem,
    @JsonProperty("id_terminal") Integer idTerminal,
    @JsonProperty("price_buy") Double priceBuy,
    @JsonProperty("price_sell") Double priceSell,
    @JsonProperty("date_modified") Long dateModified) {}
