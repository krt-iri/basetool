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
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties under {@code app.keycloak.sync.*}.
 *
 * <p>Drives {@link de.greluc.krt.profit.basetool.backend.task.UserSyncTask}: the admin URL, realm
 * and client credentials let the backend authenticate against the Keycloak Admin API; {@code
 * interval} sets the fixed-delay cadence; {@code enabled} short-circuits the task in environments
 * where the Admin API is unreachable (e.g. CI). All values are validated at startup so a missing
 * secret fails the boot rather than producing 401s at the first scheduled run.
 */
@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "app.keycloak.sync")
public class KeycloakSyncProperties {
  /** Whether to enable the periodic user sync. */
  private boolean enabled = true;

  /** Interval for the sync task. Default is 5 minutes. */
  @NotNull private Duration interval = Duration.ofMinutes(5);

  /** Keycloak base URL for admin API (e.g. http://localhost:8080). */
  @NotBlank @URL private String adminUrl;

  /** Realm to sync users from. */
  @NotBlank private String realm;

  /** Client ID for admin access (must have manage-users or view-users role). */
  @NotBlank private String clientId;

  /** Client Secret for admin access. */
  @NotBlank private String clientSecret;
}
