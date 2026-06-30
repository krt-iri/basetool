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

package de.greluc.krt.profit.basetool.frontend.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Trusted reverse-proxy allowlist for originating-client-IP attribution (REQ-SEC-011, finding
 * SEC-02).
 *
 * <p>The frontend resolves the real client IP it relays to the backend's per-IP rate limiter (via
 * {@code ClientIpContextFilter} → {@code ClientIpRelayFilter}). It honours an {@code
 * X-Forwarded-For} header <b>only</b> when the immediate TCP peer ({@code request.getRemoteAddr()})
 * matches one of these entries — the production reverse proxy (nginx-proxy-manager) on the internal
 * Docker network. Without this allowlist the forwarded-headers handling trusts the
 * <em>client-supplied</em>, leftmost {@code X-Forwarded-For} value unconditionally, letting an
 * unauthenticated caller mint a fresh per-IP rate-limit bucket per request by rotating a forged
 * header (finding SEC-02).
 *
 * <p>Each entry is an exact IP ({@code 172.18.0.5}) or a CIDR range ({@code 172.18.0.0/16}); the
 * literal {@code "*"} is intentionally NOT honoured, since blanket trust re-opens the spoof. The
 * default is empty: in dev/test no proxy fronts the frontend, so the raw TCP peer is used and a
 * client-supplied {@code X-Forwarded-For} is ignored. Production sets the Docker proxy range in
 * {@code application-prod.yml} (override via {@code APP_CLIENT_IP_TRUSTED_PROXIES} when the NPM
 * container sits on a different subnet).
 */
@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "app.client-ip")
public class ClientIpProperties {

  /**
   * Exact IPs / CIDR ranges of the trusted reverse proxies whose {@code X-Forwarded-For} the
   * frontend honours when resolving the originating client IP. Empty (dev/test default) trusts no
   * proxy, so the raw TCP peer is used and a client-supplied header cannot influence attribution.
   */
  private List<String> trustedProxies = new ArrayList<>();
}
