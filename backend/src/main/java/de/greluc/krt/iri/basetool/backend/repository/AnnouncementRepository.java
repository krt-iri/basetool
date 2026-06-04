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

package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Announcement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Announcement. */
@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

  // Fetch the single "current" announcement.
  // We assume there's basically one main announcement record that we update,
  // or if we have multiple, we fetch the most recently updated one.
  // The requirement says "ein informationsfeld". So let's stick to "findTopByOrderByUpdatedAtDesc"
  /** Returns the first matching {@code OrderByUpdatedAtDesc} (limit 1). */
  Optional<Announcement> findTopByOrderByUpdatedAtDesc();
}
