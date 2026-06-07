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
import com.microsoft.playwright.options.SelectOption;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Functional flow: add a ship to the hangar through the UI and verify it appears in the ship list.
 *
 * <p>Ships are staffel-scoped (need an OrgUnit membership) and the add-ship modal selects a
 * ShipType, which is UEX-owned — provided by the SQL catalog seed in {@link E2eStackExtension}
 * (`E2E Ship Type`). {@link BackendSeeder} seeds the IRIDIUM membership in {@link #setUp()}. The
 * ship type select (`#ship-type`) and insurance select (`#ship-insurance`) already carry stable
 * ids; the modal is opened via the `hangar-add-ship` hook and saved via `hangar-ship-submit`.
 */
@Tag("e2e")
class HangarAddShipE2eTest {

  /** Provisions (or, in staging mode, targets) the stack for the whole run. */
  @RegisterExtension static final E2eStackExtension STACK = new E2eStackExtension();

  private static final String USERNAME = System.getProperty("e2e.username", "test-admin");
  private static final String PASSWORD = System.getProperty("e2e.password", "test-admin-pw");

  private static Playwright playwright;
  private static Browser browser;

  /** Launches the browser and, for the ephemeral stack, seeds the user's IRIDIUM membership. */
  @BeforeAll
  static void setUp() {
    playwright = Playwright.create();
    browser = E2eSupport.launchBrowser(playwright, STACK.managesStack());
    if (STACK.managesStack()) {
      new BackendSeeder().ensureIridiumMembership(USERNAME, PASSWORD);
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
   * Opens the add-ship modal, picks the seeded ship type + an insurance, saves, and asserts the
   * ship then appears in the hangar list.
   */
  @Test
  void addsAShipThroughTheUi() {
    String baseUrl = STACK.baseUrl();
    Path storageState = E2eSupport.authenticatedStorageState(browser, baseUrl, USERNAME, PASSWORD);
    try (BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setIgnoreHTTPSErrors(true)
                .setStorageStatePath(storageState))) {
      Page page = context.newPage();
      try {
        page.navigate(baseUrl + "/hangar");
        page.getByTestId("hangar-add-ship").click();

        // The add-ship modal opens via JS; the ship-type / insurance selects are server-rendered.
        page.locator("#ship-type").selectOption(new SelectOption().setLabel("E2E Ship Type"));
        page.locator("#ship-insurance").selectOption("LTI");
        // Footer-safe submit: the position:fixed footer can cover the modal's save button (seen on
        // WebKit, where the coordinate click hit the footer and the ship was never created).
        E2eSupport.clickSubmitClearingFooter(page.getByTestId("hangar-ship-submit"));
        page.waitForLoadState();

        // Back on the hangar, the new ship row must show the selected ship type. Post-submit GET
        // via the retry helper — WebKit can abort it (HTTP/2 INTERNAL_ERROR) even after the
        // redirect settled. See E2eSupport#navigate.
        E2eSupport.navigate(page, baseUrl + "/hangar");
        assertThat(
                page.getByTestId("hangar-ship-row")
                    .filter(new Locator.FilterOptions().setHasText("E2E Ship Type")))
            .isVisible();
      } catch (RuntimeException | AssertionError failure) {
        E2eSupport.dump(page, "hangar-add-ship");
        throw failure;
      }
    }
  }
}
