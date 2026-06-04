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
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.LocatorAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Spezialkommando (SK) flow (UC-11): an admin manages an SK as a first-class OrgUnit, and the
 * documented limitation holds — a not-yet-profit-eligible SK cannot be the responsible (processing)
 * unit of a job order. Only profit-eligible org units process orders (V128), and a freshly created
 * SK is not profit-eligible by default, so naming it as the responsible unit returns HTTP 400.
 *
 * <ul>
 *   <li>Lifecycle (UI): the admin creates an SK via {@code /admin/special-commands} and it appears
 *       in the list.
 *   <li>Limitation (API): naming a non-profit-eligible SK as a job order's responsible OrgUnit
 *       returns 400.
 * </ul>
 */
@Tag("e2e")
class SpecialCommandE2eTest {

  /** Provisions (or, in staging mode, targets) the shared stack for the whole run. */
  @RegisterExtension static final E2eStackExtension STACK = new E2eStackExtension();

  private static final String ADMIN_USER = System.getProperty("e2e.username", "test-admin");
  private static final String ADMIN_PASSWORD = System.getProperty("e2e.password", "test-admin-pw");
  private static final String IRIDIUM_ID = "00000000-0000-0000-0000-000000000001";
  private static final String SK_UI_NAME = "E2E SK Created In UI";

  private static Playwright playwright;
  private static Browser browser;
  private static String skApiId;
  private static String materialId;

  /**
   * Launches the browser and, for the ephemeral stack, seeds the admin's IRIDIUM membership, an SK
   * (for the ownership-limitation check) and a job-order material.
   */
  @BeforeAll
  static void setUp() {
    playwright = Playwright.create();
    browser = E2eSupport.launchBrowser(playwright, STACK.managesStack());
    if (STACK.managesStack()) {
      BackendSeeder seeder = new BackendSeeder();
      seeder.ensureIridiumMembership(ADMIN_USER, ADMIN_PASSWORD);
      skApiId = seeder.createSpecialCommand(ADMIN_USER, ADMIN_PASSWORD, "E2E SK Alpha", "ESKA");
      materialId = seeder.ensureJobOrderMaterial(ADMIN_USER, ADMIN_PASSWORD, "E2E SK Material");
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

  /** The admin creates a Spezialkommando through the admin UI and it appears in the list. */
  @Test
  void adminCreatesSpecialCommandThroughTheUi() {
    String baseUrl = STACK.baseUrl();
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true))) {
      Page page = context.newPage();
      try {
        E2eSupport.login(page, baseUrl, ADMIN_USER, ADMIN_PASSWORD);
        page.navigate(baseUrl + "/admin/special-commands");
        page.waitForLoadState();
        // The create form lives in a modal opened by the "Neues Spezialkommando" button.
        page.locator("#add-sc-btn").click();
        page.locator("#sc-name").fill(SK_UI_NAME);
        page.locator("#sc-shorthand").fill("ESKU");
        E2eSupport.clickSubmitClearingFooter(page.locator("#sc-form button[type='submit']"));
        page.waitForLoadState();
        // Re-load the list fresh (as the other create flows do); asserting on the post-submit page
        // directly proved flaky. Match the new SK by its table row, not a bare text locator.
        page.navigate(baseUrl + "/admin/special-commands");
        page.waitForLoadState();
        assertThat(
                page.locator("tbody tr").filter(new Locator.FilterOptions().setHasText(SK_UI_NAME)))
            .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(20_000));
      } catch (RuntimeException | AssertionError failure) {
        E2eSupport.dump(page, "special-command-create");
        throw failure;
      }
    }
  }

  /**
   * Naming a not-yet-profit-eligible Spezialkommando as a job order's responsible (processing)
   * OrgUnit returns HTTP 400 — only profit-eligible org units may process orders, and a freshly
   * created SK is not eligible by default.
   */
  @Test
  void specialCommandCannotOwnAJobOrder() {
    int status =
        new BackendSeeder()
            .attemptCreateJobOrderStatus(
                ADMIN_USER,
                ADMIN_PASSWORD,
                skApiId,
                IRIDIUM_ID,
                "E2E SK Order",
                materialId,
                700,
                50);
    assertEquals(
        400,
        status,
        "a non-profit-eligible SK named as a job order's responsible OrgUnit must be rejected with"
            + " 400");
  }
}
