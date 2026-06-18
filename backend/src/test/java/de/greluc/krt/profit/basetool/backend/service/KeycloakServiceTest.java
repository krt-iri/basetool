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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.profit.basetool.backend.config.KeycloakSyncProperties;
import java.security.KeyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslStoreBundle;

/**
 * Unit tests for {@link KeycloakService}'s TLS-trust wiring: the constructor must pin the {@code
 * keycloak-trust} truststore when the bundle exists (production) and quietly fall back to the
 * default client when it does not (dev/test plain-HTTP admin URL).
 */
@ExtendWith(MockitoExtension.class)
class KeycloakServiceTest {

  @Mock private SslBundles sslBundles;

  /**
   * When the {@code keycloak-trust} SSL bundle is present, the constructor must parse its
   * truststore and build the pinned request factory without error.
   */
  @Test
  void constructor_withKeycloakTrustBundle_buildsTrustedClient() throws Exception {
    KeyStore truststore = KeyStore.getInstance("PKCS12");
    truststore.load(null, null);
    SslStoreBundle stores = mock(SslStoreBundle.class);
    when(stores.getTrustStore()).thenReturn(truststore);
    SslBundle bundle = mock(SslBundle.class);
    when(bundle.getStores()).thenReturn(stores);
    when(sslBundles.getBundle("keycloak-trust")).thenReturn(bundle);

    assertDoesNotThrow(() -> new KeycloakService(new KeycloakSyncProperties(), sslBundles));
    verify(sslBundles).getBundle("keycloak-trust");
  }

  /**
   * When no {@code keycloak-trust} bundle is configured (dev/test), the constructor must swallow
   * the {@link NoSuchSslBundleException} and the service must still operate — here a disabled sync
   * simply returns no users instead of failing.
   */
  @Test
  void fetchUsers_withoutBundle_disabledSync_returnsEmpty() {
    when(sslBundles.getBundle("keycloak-trust"))
        .thenThrow(new NoSuchSslBundleException("keycloak-trust", "no such bundle"));
    KeycloakSyncProperties properties = new KeycloakSyncProperties();
    properties.setEnabled(false);

    KeycloakService service = new KeycloakService(properties, sslBundles);

    assertTrue(service.fetchUsers().isEmpty());
    verify(sslBundles).getBundle("keycloak-trust");
  }
}
