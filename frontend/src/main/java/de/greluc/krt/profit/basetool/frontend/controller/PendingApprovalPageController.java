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

package de.greluc.krt.profit.basetool.frontend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Renders the "waiting for admin approval" page (epic #720, Track 1). A brand-new Discord user
 * lands here — they are authenticated but their only authority is {@code ROLE_PENDING_APPROVAL}, so
 * {@link de.greluc.krt.profit.basetool.frontend.config.BackendRoleSyncFilter} redirects every other
 * request here until an admin approves them. The page itself is exempt from that redirect (else it
 * would loop).
 */
@Controller
@PreAuthorize("isAuthenticated()")
public class PendingApprovalPageController {

  /**
   * Renders the waiting-for-approval page.
   *
   * @return the {@code pending-approval} view name
   */
  @GetMapping("/pending-approval")
  public String pendingApproval() {
    return "pending-approval";
  }
}
