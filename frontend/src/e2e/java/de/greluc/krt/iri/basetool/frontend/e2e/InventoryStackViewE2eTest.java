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

package de.greluc.krt.iri.basetool.frontend.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.LocatorAssertions;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Regression flow for the group-on-read Lager (ADR-0003, REQ-INV-002): a non-personal stock row
 * linked to <em>neither</em> a job order <em>nor</em> a mission — the overwhelmingly common case —
 * must still appear in the squadron-wide grouped view at {@code /inventory/all}.
 *
 * <p>The v0.4.0 group-on-read queries projected and grouped the nullable {@code jobOrder} / {@code
 * mission} / {@code owningOrgUnit} associations as whole entities, which rendered implicit INNER
 * joins and silently dropped every stack missing those links. The result: {@code /inventory/all}
 * (and {@code /inventory/my}) showed "Keine Einträge gefunden" even though the per-material
 * aggregate page ({@code /inventory}) still listed the stock. This test seeds exactly such a row via
 * the backend API and asserts the material surfaces as a group row in the grouped UI — the data-only
 * counterpart is {@code InventoryItemStackQueryDataTest}.
 *
 * <p>The assertion targets the group-header row by its {@code data-material-id} (only emitted when
 * {@code groupedItems} is non-empty) rather than the material name, because the name also appears in
 * the filter dropdown and would match even when the grouped table is empty.
 */
@Tag("e2e")
class InventoryStackViewE2eTest {

  /** Provisions (or, in staging mode, targets) the stack for the whole run. */
  @RegisterExtension static final E2eStackExtension STACK = new E2eStackExtension();

  private static final String USERNAME = System.getProperty("e2e.username", "test-admin");
  private static final String PASSWORD = System.getProperty("e2e.password", "test-admin-pw");
  private static final String MATERIAL_NAME = "E2E Stack View Material";

  private static Playwright playwright;
  private static Browser browser;
  private static String materialId;

  /**
   * Launches the browser and, for the ephemeral stack, seeds the IRIDIUM membership plus a single
   * non-personal stock row that has no job order and no mission.
   */
  @BeforeAll
  static void setUp() {
    playwright = Playwright.create();
    browser = E2eSupport.launchBrowser(playwright, STACK.managesStack());
    if (STACK.managesStack()) {
      BackendSeeder seeder = new BackendSeeder();
      seeder.ensureIridiumMembership(USERNAME, PASSWORD);
      materialId = seeder.createRefineryMaterial(USERNAME, PASSWORD, MATERIAL_NAME);
      String locationId = seeder.createLocation(USERNAME, PASSWORD, "E2E Stack View Hub");
      // Non-personal, no job order, no mission (owningOrgUnit auto-stamped to IRIDIUM): the exact
      // row shape the implicit-inner-join regression dropped from the grouped view.
      seeder.createInventoryItem(USERNAME, PASSWORD, materialId, locationId, 800, 100.0);
    }
  }

  /** Releases the browser and the Playwright driver process. */
  @AfterAll
  static void tearDown() {
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
  }

  /**
   * Loads {@code /inventory/all} and asserts the seeded, unlinked stock surfaces as a material group
   * row — i.e. the grouped view is not empty for stock that carries no job order / mission.
   */
  @Test
  void groupedGlobalInventoryShowsStockWithoutJobOrderOrMission() {
    String baseUrl = STACK.baseUrl();
    Path storageState = E2eSupport.authenticatedStorageState(browser, baseUrl, USERNAME, PASSWORD);
    try (BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setIgnoreHTTPSErrors(true)
                .setStorageStatePath(storageState))) {
      Page page = context.newPage();
      try {
        page.navigate(baseUrl + "/inventory/all");
        page.waitForLoadState();
        // Before the LEFT-JOIN fix the grouped table came back empty ("Keine Einträge gefunden"),
        // so no group-header row existed; the seeded material's group row must now be present.
        // 20 s, not the 5 s default: the grouped render is slow on WebKit under CI load.
        assertThat(page.locator("tr.group-header[data-material-id='" + materialId + "']"))
            .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(20_000));
      } catch (RuntimeException | AssertionError failure) {
        E2eSupport.dump(page, "inventory-stack-view");
        throw failure;
      }
    }
  }
}
