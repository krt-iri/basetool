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

package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.greluc.krt.iri.basetool.backend.model.dto.P4kImportResultDto;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Opt-in end-to-end verification that {@link P4kImportService} consumes a <em>real</em> KRT P4K
 * Reader catalog (the full ~60k-record {@code Data/Game2.dcb} export, not a synthetic fixture)
 * without error, against the real Testcontainers Postgres of the {@code test} profile.
 *
 * <p>It is gated on the presence of a catalog file at {@code backend/build/p4k-catalog-verify.json}
 * (the working directory of the test JVM is the {@code backend} module dir, and {@code build/} is
 * gitignored): when the file is absent — as in CI — the test {@link Assumptions assumes} its way to
 * a skip, so it never breaks the pipeline. To run it locally, drop a catalog there and execute
 * {@code ./gradlew :backend:test --tests "*P4kImportRealCatalogVerificationTest"}.
 *
 * <p>The assertion that matters is simply that {@link P4kImportService#previewImport(byte[])}
 * returns rather than throwing: a successful preview means every one of the catalog's tens of
 * thousands of manufacturer / item / ship / commodity / blueprint records deserialized into the
 * {@code P4k*Dto} shapes and ran through the reconciliation chain. (A fresh test database carries
 * no UEX / SC-Wiki master data, so the records resolve as would-be seeds / unmatched rather than
 * matches — the point is coverage of the parse + classify path across the whole file, not match
 * counts.) {@link JwtDecoder} is mocked so the resource-server context boots without Keycloak.
 */
@SpringBootTest
@ActiveProfiles("test")
class P4kImportRealCatalogVerificationTest {

  /** Working-directory-relative, gitignored location an operator drops a real catalog into. */
  private static final Path CATALOG = Path.of("build", "p4k-catalog-verify.json");

  @Autowired private P4kImportService service;

  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void preview_realCatalog_parsesAndReconcilesEntireFileWithoutError() throws Exception {
    Assumptions.assumeTrue(
        Files.isReadable(CATALOG),
        () ->
            "Opt-in verification skipped: drop a real P4K catalog JSON at "
                + CATALOG.toAbsolutePath()
                + " to run it.");

    byte[] bytes = Files.readAllBytes(CATALOG);
    assertTrue(bytes.length > 0, "the staged catalog must not be empty");

    P4kImportResultDto result = service.previewImport(bytes);

    assertNotNull(result, "preview returned a result");
    assertTrue(result.dryRun(), "preview is a dry run");

    int itemsTotal =
        result.items().matched() + result.items().created() + result.items().unmatched();
    assertTrue(itemsTotal > 0, "the preview classified item records from the catalog");

    System.out.println(
        "P4K real-catalog preview OK — manufacturers"
            + fmt(result.manufacturers())
            + " items"
            + fmt(result.items())
            + " ships"
            + fmt(result.ships())
            + " commodities"
            + fmt(result.commodities())
            + " blueprints"
            + fmt(result.blueprints())
            + " ingredientsResolved="
            + result.ingredientsResolved());
  }

  private static String fmt(P4kImportResultDto.Counts c) {
    return "[m=" + c.matched() + ",cr=" + c.created() + ",un=" + c.unmatched() + "]";
  }
}
