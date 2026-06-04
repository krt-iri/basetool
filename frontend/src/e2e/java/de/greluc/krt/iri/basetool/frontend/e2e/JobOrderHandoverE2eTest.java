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
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Functional flow: record a Job Order handover through the UI and verify it appears in the order's
 * handover table.
 *
 * <p>This is the concurrency-sensitive flow — the handover decrements the linked inventory item and
 * the job-order material's open amount inside one transaction. It needs an order with a
 * handover-eligible inventory item linked to it, which the admin REST API can build end to end:
 * {@link BackendSeeder} seeds the IRIDIUM membership, a job-order material, a location, a job order
 * requesting that material, and an inventory item linked to the order. The handover modal lazily
 * fetches that linked inventory per material and snapshots it into the item dropdown when a row is
 * added, so the test waits for the cache before adding a row.
 */
@Tag("e2e")
class JobOrderHandoverE2eTest {

  /** Provisions (or, in staging mode, targets) the stack for the whole run. */
  @RegisterExtension static final E2eStackExtension STACK = new E2eStackExtension();

  private static final String USERNAME = System.getProperty("e2e.username", "test-admin");
  private static final String PASSWORD = System.getProperty("e2e.password", "test-admin-pw");
  private static final String IRIDIUM_ID = "00000000-0000-0000-0000-000000000001";

  private static Playwright playwright;
  private static Browser browser;
  private static String jobOrderId;
  private static String inventoryItemId;

  /**
   * Launches the browser and, for the ephemeral stack, seeds the full handover precondition chain:
   * IRIDIUM membership, a job-order material, a location, a job order requesting that material, and
   * an inventory item linked to that order.
   */
  @BeforeAll
  static void setUp() {
    playwright = Playwright.create();
    browser = E2eSupport.launchBrowser(playwright, STACK.managesStack());
    if (STACK.managesStack()) {
      BackendSeeder seeder = new BackendSeeder();
      seeder.ensureIridiumMembership(USERNAME, PASSWORD);
      String materialId =
          seeder.ensureJobOrderMaterial(USERNAME, PASSWORD, "E2E Handover Material");
      String locationId = seeder.createLocation(USERNAME, PASSWORD, "E2E Handover Location");
      jobOrderId =
          seeder.createJobOrder(
              USERNAME, PASSWORD, IRIDIUM_ID, "E2E Handover Order", materialId, 700, 100.0);
      inventoryItemId =
          seeder.createInventoryItemForJobOrder(
              USERNAME, PASSWORD, materialId, locationId, jobOrderId, 750, 100.0);
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
   * Opens the handover modal, picks the seeded linked inventory item plus an amount, fills the
   * recipient and handover time, submits, and asserts the handover then appears in the order's
   * handover table.
   */
  @Test
  void recordsAHandoverThroughTheUi() {
    String baseUrl = STACK.baseUrl();
    Path storageState = E2eSupport.authenticatedStorageState(browser, baseUrl, USERNAME, PASSWORD);
    try (BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setIgnoreHTTPSErrors(true)
                .setStorageStatePath(storageState))) {
      Page page = context.newPage();
      try {
        page.navigate(baseUrl + "/orders/" + jobOrderId);

        // Opening the modal lazily fetches the order's linked inventory per material; the "add row"
        // button snapshots that cache at click time, so the cache must be populated first. Gate on
        // the network response rather than a page-side waitForFunction, which would trip the strict
        // CSP (script-src has no 'unsafe-eval').
        page.waitForResponse(
            response ->
                response.url().contains("/materials/") && response.url().contains("/inventory"),
            () -> page.getByTestId("order-handover-open").click());

        page.locator("#add-handover-item-btn").click();
        page.locator("select[name='items[0].inventoryItemId']").selectOption(inventoryItemId);
        page.locator("input[name='items[0].amount']").fill("50");

        // The split date/time inputs sync into the hidden #handoverTime (UTC ISO); past times are
        // allowed here (data-validate-not-past='false'), so today's date is fine.
        page.locator("#handover-modal .date-part").fill(LocalDate.now().toString());
        page.locator("#handover-modal .time-part").fill("12:00");
        page.locator("#recipientHandle").fill("E2E Recipient");

        page.getByTestId("order-handover-submit").click();
        page.waitForLoadState();

        // The recorded handover must appear in the order's handover table.
        page.navigate(baseUrl + "/orders/" + jobOrderId);
        assertThat(
                page.getByTestId("order-handover-row")
                    .filter(new Locator.FilterOptions().setHasText("E2E Recipient")))
            .isVisible();
      } catch (RuntimeException | AssertionError failure) {
        E2eSupport.dump(page, "joborder-handover");
        throw failure;
      }
    }
  }
}
