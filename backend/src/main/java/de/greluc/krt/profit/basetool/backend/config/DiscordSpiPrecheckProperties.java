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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties under {@code app.discord.spi-precheck.*} (REQ-SEC-022).
 *
 * <p>Guards the internal {@code POST /internal/discord/account-existence} endpoint that the
 * Keycloak Discord SPI calls during first-broker-login to learn whether a Basetool account already
 * exists for an incoming Discord identity. The shared secret is the only credential on that
 * endpoint (it sits outside the OAuth2 resource-server boundary), presented by the SPI in the
 * {@code X-KRT-SPI-Secret} header and compared in constant time by {@link
 * de.greluc.krt.profit.basetool.backend.controller.DiscordAccountExistenceController}.
 *
 * <p>The secret is <strong>optional</strong> (default blank) on purpose: a deployment that does not
 * use Discord login must still boot. A blank secret <em>disables</em> the endpoint (it answers
 * {@code 503}); the SPI treats any non-200 as unknown and fails open, so the collision precheck is
 * simply skipped. Never commit a real value — it is supplied via the {@code
 * KRT_DISCORD_SPI_SHARED_SECRET} environment variable.
 */
@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "app.discord.spi-precheck")
public class DiscordSpiPrecheckProperties {

  /**
   * Shared secret the Keycloak SPI must present to call the account-existence endpoint. Blank (the
   * default) disables the endpoint, which fail-open-skips the precheck on the SPI side. Not a
   * {@code @NotBlank} so a non-Discord deployment boots without it.
   */
  private String sharedSecret = "";
}
