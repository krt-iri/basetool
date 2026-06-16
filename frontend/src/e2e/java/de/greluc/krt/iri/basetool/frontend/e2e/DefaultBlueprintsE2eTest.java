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
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * End-to-end coverage for the default-blueprint feature (REQ-INV-016/017) against the live stack.
 *
 * <p>The default blueprints are seeded into {@code default_blueprint} at backend startup (the
 * provisioning bootstrap; enabled in the {@code dev} profile the e2e stack runs) and granted to a
 * user the first time their {@code app_user} row is created — which {@link BackendSeeder#getUserId}
 * forces in {@link #setUp()}. Two flows are asserted:
 *
 * <ul>
 *   <li><b>user-facing non-removability:</b> a granted default appears in the owner's blueprint
 *       list and its detail pane offers the edit control but <em>no</em> delete control (the {@code
 *       removable=false} flag hides it), which is the exact UX the owner chose over a 409.
 *   <li><b>admin curation:</b> the admin default-blueprints page lists the seeded set, and removing
 *       one entry through the confirm modal drops it from the set.
 * </ul>
 *
 * <p>Removing an entry in the admin flow does not revoke rows users already hold, so the two tests
 * are order-independent on the shared ephemeral stack.
 */
@Tag("e2e")
class DefaultBlueprintsE2eTest {

  /** Provisions (or, in staging mode, targets) the stack for the whole run. */
  @RegisterExtension static final E2eStackExtension STACK = new E2eStackExtension();

  private static final String USERNAME = System.getProperty("e2e.username", "test-admin");
  private static final String PASSWORD = System.getProperty("e2e.password", "test-admin-pw");

  private static Playwright playwright;
  private static Browser browser;

  /**
   * Launches the browser and, for the ephemeral stack, forces the test user's first login so their
   * {@code app_user} row is created and the default blueprints are granted before the flows run.
   */
  @BeforeAll
  static void setUp() {
    playwright = Playwright.create();
    browser = E2eSupport.launchBrowser(playwright, STACK.managesStack());
    if (STACK.managesStack()) {
      // Materialises the user row → fires the after-commit provisioning event → grants the
      // defaults.
      new BackendSeeder().getUserId(USERNAME, PASSWORD);
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
   * Opens the owner's blueprint list, selects an auto-granted default, and asserts the detail pane
   * shows the edit control but hides the delete control — the hidden-delete UX the owner chose.
   */
  @Test
  void defaultBlueprintOffersNoDeleteControl() {
    String baseUrl = STACK.baseUrl();
    Path storageState = E2eSupport.authenticatedStorageState(browser, baseUrl, USERNAME, PASSWORD);
    try (BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setIgnoreHTTPSErrors(true)
                .setStorageStatePath(storageState))) {
      Page page = context.newPage();
      try {
        E2eSupport.navigate(page, baseUrl + "/personal-inventory/blueprints");
        page.waitForLoadState();

        Locator rows = page.locator("#krt-bp-master-rows .master-row");
        // The user owns only the granted defaults on a fresh stack, so the first row is a default.
        assertThat(rows.first())
            .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(10_000));
        assertEquals(
            "false",
            rows.first().getAttribute("data-removable"),
            "an auto-granted default blueprint must be flagged non-removable");

        rows.first().click();

        // The detail pane renders with the edit control but the delete control stays hidden.
        assertThat(page.locator("#krt-bp-detail-edit")).isVisible();
        assertThat(page.locator("#krt-bp-detail-delete")).isHidden();
      } catch (RuntimeException | AssertionError failure) {
        E2eSupport.dump(page, "default-blueprint-no-delete");
        throw failure;
      }
    }
  }

  /**
   * Opens the admin default-blueprints page, asserts the seeded set is listed, removes one entry
   * through the confirm modal, and asserts the set shrank by one after the redirect.
   */
  @Test
  void adminCanRemoveADefaultFromTheSet() {
    String baseUrl = STACK.baseUrl();
    Path storageState = E2eSupport.authenticatedStorageState(browser, baseUrl, USERNAME, PASSWORD);
    try (BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setIgnoreHTTPSErrors(true)
                .setStorageStatePath(storageState))) {
      Page page = context.newPage();
      try {
        E2eSupport.navigate(page, baseUrl + "/admin/default-blueprints");
        page.waitForLoadState();

        Locator removeButtons = page.locator("[data-trigger='dbp-open-delete']");
        assertThat(removeButtons.first())
            .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(10_000));
        int before = removeButtons.count();

        // Open the confirm modal for the first default, then submit the classic POST→redirect.
        removeButtons.first().click();
        assertThat(page.locator("#krt-dbp-delete-modal")).isVisible();
        // The confirm button JS-submits the selected row's server-rendered POST form.
        Locator submit = page.locator("#krt-dbp-delete-confirm");
        E2eSupport.clickSubmitClearingFooter(submit);

        int after = page.locator("[data-trigger='dbp-open-delete']").count();
        assertEquals(
            before - 1, after, "removing a default must drop exactly one entry from the set");
      } catch (RuntimeException | AssertionError failure) {
        E2eSupport.dump(page, "default-blueprint-admin-remove");
        throw failure;
      }
    }
  }
}
