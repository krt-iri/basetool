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

package de.greluc.krt.profit.basetool.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties under {@code krt.uex.*}.
 *
 * <p>Holds the UEX (uexcorp.space) base URL and every endpoint path used by {@link
 * de.greluc.krt.profit.basetool.backend.integration.UexClient}. Endpoints are kept here, not
 * hardcoded in the client, so a UEX schema rename (e.g. {@code /commodities_prices_all} to a new
 * path) is a one-line config change.
 *
 * <p>{@code schedulerEnabled} toggles the periodic background sync; {@code schedulerDelay} is the
 * fixed-delay between successive sync runs in milliseconds. Defaults to once a day (24 h) — UEX
 * commodity prices and the catalogue move slowly enough that a daily refresh keeps the data fresh
 * without hammering the upstream API. Carries {@code @EnableScheduling} and {@code @EnableAsync}
 * because it is the single owner of the sync timing — putting these in a standalone configuration
 * class would have led to two unrelated {@code @Configuration} classes sharing the same purpose.
 */
@Data
@Validated
@Configuration
@EnableScheduling
@EnableAsync
@ConfigurationProperties(prefix = "krt.uex")
public class UexProperties {

  @NotBlank private String apiUrl = "https://api.uexcorp.space/2.0";

  @NotBlank private String commoditiesEndpoint = "/commodities";

  @NotBlank private String commoditiesPricesEndpoint = "/commodities_prices_all";

  @NotBlank private String starSystemsEndpoint = "/star_systems";

  @NotBlank private String companiesEndpoint = "/companies";

  @NotBlank private String vehiclesEndpoint = "/vehicles";

  @NotBlank private String citiesEndpoint = "/cities";

  @NotBlank private String factionsEndpoint = "/factions";

  @NotBlank private String jurisdictionsEndpoint = "/jurisdictions";

  @NotBlank private String moonsEndpoint = "/moons";

  @NotBlank private String orbitsEndpoint = "/orbits";

  @NotBlank private String outpostsEndpoint = "/outposts";

  @NotBlank private String planetsEndpoint = "/planets";

  @NotBlank private String poiEndpoint = "/poi";

  @NotBlank private String spaceStationsEndpoint = "/space_stations";

  @NotBlank private String terminalsEndpoint = "/terminals";

  @NotBlank private String refineriesMethodsEndpoint = "/refineries_methods";

  @NotBlank private String refineriesYieldsEndpoint = "/refineries_yields";

  /**
   * R2 item-catalogue endpoint. Filtered call-site: {@code /items?id_category=<n>} — see {@code
   * UexItemSyncService}. Walking every category requires 98+ round-trips, which is paced at the
   * same default cadence as the rest of the UEX sync.
   */
  @NotBlank private String itemsEndpoint = "/items";

  /**
   * R7 item-price endpoint (~1 MB+ payload, similar shape to {@code /commodities_prices_all}).
   * Feature-flagged via {@code krt.uex.item-price-sync-enabled}; R2 ships only the property +
   * client method, not the sync service.
   */
  @NotBlank private String itemsPricesEndpoint = "/items_prices_all";

  /**
   * R2 category reference endpoint. Drives the UEX item walk through {@code UexCategoryRefService};
   * the response shape carries the 98 (or more) {@code (id, type, section, name)} tuples that map
   * each item / vehicle to its grouping.
   */
  @NotBlank private String categoriesEndpoint = "/categories";

  /**
   * Master switch for the R7 {@code UexItemPriceSyncService}. R2 ships the property only; the sync
   * service lands in R7. Defaults to {@code false} so an accidental flip on a non-R7 build is a
   * no-op.
   */
  @NotNull private Boolean itemPriceSyncEnabled = false;

  @NotNull private Boolean schedulerEnabled = true;

  @NotBlank private String schedulerDelay = "86400000";
}
