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

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Form-binding object for the admin member-edit page.
 *
 * <p>SPEZIALKOMMANDO_PLAN.md §7.4: {@link #isLogistician} / {@link #isMissionManager} carry the
 * per-Staffel-membership role flags that the page-controller diffs against the loaded state and
 * forwards to the new single-POST {@code PATCH /api/v1/users/{id}/memberships} delta endpoint —
 * concentrating the previously-fragmented {@code /users/{id}/logistician} + {@code
 * /users/{id}/mission-manager} round-trips into one transactional save.
 *
 * @param rank pay-grade rank (1-20).
 * @param description profile description.
 * @param displayName visible display name.
 * @param version {@code app_user} row {@code @Version} for the optimistic-lock check.
 * @param source origin marker — {@code "profile"} keeps the round-trip on the profile page,
 *     anything else lands back on the member list.
 * @param joinDate squadron-join date.
 * @param squadronId Staffel assignment target, or {@code null} to clear.
 * @param isLogistician desired post-save value of the Staffel-membership Logistician flag, or
 *     {@code null} when the section was not rendered (no Staffel) so the flag delta is skipped.
 * @param isMissionManager desired post-save value of the Staffel-membership Mission Manager flag,
 *     or {@code null} when the section was not rendered.
 */
public record MemberEditForm(
    Integer rank,
    @Size(max = 2000, message = "{validation.description.max}") String description,
    @Size(max = 255, message = "{validation.displayname.max}") String displayName,
    Long version,
    String source,
    @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinDate,
    @Nullable UUID squadronId,
    @Nullable Boolean isLogistician,
    @Nullable Boolean isMissionManager) {}
