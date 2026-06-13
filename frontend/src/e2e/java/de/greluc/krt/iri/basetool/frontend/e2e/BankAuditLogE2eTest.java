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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.LocatorAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Admin audit-log viewer end to end (REQ-BANK-012, epic #556): a bank mutation writes an audit row,
 * and the admin A2 viewer lists it and filters by event type. The viewer is admin-only — the
 * permission carve-out itself is covered by {@link BankPermissionsE2eTest}.
 */
@Tag("e2e")
class BankAuditLogE2eTest {

  /** Provisions (or, in staging mode, targets) the shared stack for the whole run. */
  @RegisterExtension static final E2eStackExtension STACK = new E2eStackExtension();

  private static final String ADMIN_USER = System.getProperty("e2e.username", "test-admin");
  private static final String ADMIN_PASSWORD = System.getProperty("e2e.password", "test-admin-pw");
  private static final String MGMT_USER = "test-bank-management";
  private static final String MGMT_PASSWORD = "test-bank-management-pw";
  private static final String EMPLOYEE_USER = "test-bank-employee";
  private static final String EMPLOYEE_PASSWORD = "test-bank-employee-pw";

  private static Playwright playwright;
  private static Browser browser;
  private static BackendSeeder seeder;

  @BeforeAll
  static void setUp() {
    playwright = Playwright.create();
    browser = E2eSupport.launchBrowser(playwright, STACK.managesStack());
    if (!STACK.managesStack()) {
      return;
    }
    seeder = new BackendSeeder();
    // Generate a deposit so a DEPOSIT_BOOKED audit row is guaranteed to exist for the viewer.
    String employeeId = seeder.getUserId(EMPLOYEE_USER, EMPLOYEE_PASSWORD);
    String accountId =
        seeder.createBankAccount(MGMT_USER, MGMT_PASSWORD, "E2E Audit Account", "SPECIAL");
    String holderId = seeder.registerBankHolder(MGMT_USER, MGMT_PASSWORD, employeeId);
    seeder.bankDeposit(MGMT_USER, MGMT_PASSWORD, accountId, holderId, 1234);
  }

  @AfterAll
  static void tearDown() {
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
  }

  /** The admin audit viewer lists rows, and filtering by the deposit event keeps showing rows. */
  @Test
  void adminAuditViewerListsAndFiltersEvents() {
    String baseUrl = STACK.baseUrl();
    try (BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true))) {
      Page page = context.newPage();
      try {
        E2eSupport.login(page, baseUrl, ADMIN_USER, ADMIN_PASSWORD);
        E2eSupport.navigate(page, baseUrl + "/admin/bank-audit");
        page.waitForLoadState();
        assertThat(page.locator("[data-testid='bank-audit-panel']"))
            .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(20_000));
        // Web-first wait before the one-shot count(): rows paint shortly after the panel, and on
        // slower engines (webkit) a bare count() races that paint and reads 0. isVisible
        // auto-retries.
        assertThat(page.locator("[data-testid='bank-audit-row']").first())
            .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(20_000));
        assertTrue(
            page.locator("[data-testid='bank-audit-row']").count() >= 1,
            "the audit viewer lists at least one event");

        // Filter to DEPOSIT_BOOKED and apply — the seeded deposit keeps the table non-empty.
        page.locator("[data-testid='bank-audit-filter-event']").selectOption("DEPOSIT_BOOKED");
        E2eSupport.awaitFormPost(
            page, () -> page.locator("[data-testid='bank-audit-filter-apply']").click());
        page.waitForLoadState();
        // Same web-first wait after the filter form-post before reading count() (the webkit flake).
        assertThat(page.locator("[data-testid='bank-audit-row']").first())
            .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(20_000));
        assertTrue(
            page.locator("[data-testid='bank-audit-row']").count() >= 1,
            "filtering by DEPOSIT_BOOKED still lists the seeded deposit");
      } catch (RuntimeException | AssertionError failure) {
        E2eSupport.dump(page, "bank-audit-viewer");
        throw failure;
      }
    }
  }
}
