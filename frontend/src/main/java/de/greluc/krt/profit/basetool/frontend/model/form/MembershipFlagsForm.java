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

package de.greluc.krt.profit.basetool.frontend.model.form;

/**
 * Form-binding object for the per-membership Logistician / Mission Manager flag flip on the admin
 * Spezialkommando detail page.
 *
 * <p>Modeled as a {@code record} on purpose. The HTML form carries field names {@code
 * isLogistician} and {@code isMissionManager} verbatim, but a Lombok {@code @Data} POJO with a
 * {@code boolean isLogistician} field would expose the JavaBean property as {@code logistician}
 * (Lombok strips the {@code is} prefix in the setter, and Spring derives the property name from the
 * setter). That mismatch made the obvious form name unbindable. A record uses each component name
 * as the property name directly — {@code isLogistician} stays {@code isLogistician} — and Spring's
 * {@link org.springframework.web.bind.ServletRequestDataBinder#checkFieldDefaults} step still picks
 * up the {@code _<field>} hidden marker the form template emits before each checkbox, so an
 * unchecked box surfaces as {@code false} instead of being missing from the payload.
 *
 * <p>Primitive {@code boolean} (not boxed {@code Boolean}): the hidden {@code _field} marker
 * guarantees the field is always present, so the bind cannot leave the component as {@code null} —
 * and a primitive removes the ambiguous "null = no change" semantic that the backend patch DTO
 * needs for direct API callers, but the admin UI does not.
 *
 * @param isLogistician new value of the Logistician flag.
 * @param isMissionManager new value of the Mission Manager flag.
 * @param version optimistic-lock counter held by the form; required for the backend's version
 *     check.
 */
public record MembershipFlagsForm(boolean isLogistician, boolean isMissionManager, Long version) {}
