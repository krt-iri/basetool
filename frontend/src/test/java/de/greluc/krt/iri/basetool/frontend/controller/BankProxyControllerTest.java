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

package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BankProxyControllerTest {

  private BackendApiClient backendApiClient;
  private BankProxyController controller;

  @BeforeEach
  void setUp() {
    backendApiClient = mock(BackendApiClient.class);
    controller = new BankProxyController(backendApiClient);
  }

  @Test
  void bookDeposit_ShouldForwardBodyToBackend() {
    // Given
    Map<String, Object> body = Map.of("accountId", "a", "holderId", "h", "amount", 100);
    when(backendApiClient.post("/api/v1/bank/deposits", body, Map.class))
        .thenReturn(Map.of("id", "x"));

    // When
    Map<String, Object> result = controller.bookDeposit(body);

    // Then
    assertEquals("x", result.get("id"));
    verify(backendApiClient).post("/api/v1/bank/deposits", body, Map.class);
  }

  @Test
  void bookTransfer_ShouldReturnEmptyMapForBodylessResponse() {
    // Given
    Map<String, Object> body = Map.of("sourceAccountId", "a");
    when(backendApiClient.post("/api/v1/bank/transfers", body, Map.class)).thenReturn(null);

    // When
    Map<String, Object> result = controller.bookTransfer(body);

    // Then
    assertEquals(Map.of(), result);
  }

  @Test
  void reverseTransaction_ShouldForwardEmptyMapWhenBodyMissing() {
    // Given
    UUID id = UUID.randomUUID();
    when(backendApiClient.post(
            eq("/api/v1/bank/transactions/" + id + "/reversal"), eq(Map.of()), eq(Map.class)))
        .thenReturn(Map.of());

    // When
    controller.reverseTransaction(id, null);

    // Then
    verify(backendApiClient)
        .post("/api/v1/bank/transactions/" + id + "/reversal", Map.of(), Map.class);
  }

  @Test
  void renameAccount_ShouldPatchBackend() {
    // Given
    UUID id = UUID.randomUUID();
    Map<String, Object> body = Map.of("name", "Neu", "version", 1);
    when(backendApiClient.patch("/api/v1/bank/accounts/" + id, body, Map.class))
        .thenReturn(Map.of("name", "Neu"));

    // When
    Map<String, Object> result = controller.renameAccount(id, body);

    // Then
    assertEquals("Neu", result.get("name"));
  }

  @Test
  void closeAndReopen_ShouldPostLifecycleEndpoints() {
    // Given
    UUID id = UUID.randomUUID();
    Map<String, Object> body = Map.of("version", 2);

    // When
    controller.closeAccount(id, body);
    controller.reopenAccount(id, body);

    // Then
    verify(backendApiClient).post("/api/v1/bank/accounts/" + id + "/close", body, Map.class);
    verify(backendApiClient).post("/api/v1/bank/accounts/" + id + "/reopen", body, Map.class);
  }

  @Test
  void updateHolder_ShouldPatchBackend() {
    // Given
    UUID id = UUID.randomUUID();
    Map<String, Object> body = Map.of("active", false, "version", 0);

    // When
    controller.updateHolder(id, body);

    // Then
    verify(backendApiClient).patch("/api/v1/bank/holders/" + id, body, Map.class);
  }

  @Test
  void grantLifecycle_ShouldTargetCompositeKeyPaths() {
    // Given
    UUID userId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    Map<String, Object> flags =
        Map.of("canDeposit", true, "canWithdraw", false, "canTransfer", true, "version", 3);

    // When
    controller.updateGrant(userId, accountId, flags);
    controller.deleteGrant(userId, accountId);

    // Then
    verify(backendApiClient)
        .patch("/api/v1/bank/grants/" + userId + "/" + accountId, flags, Map.class);
    verify(backendApiClient).delete("/api/v1/bank/grants/" + userId + "/" + accountId, Void.class);
  }
}
