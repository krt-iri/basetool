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

package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.Outpost;
import de.greluc.krt.iri.basetool.backend.repository.OutpostRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read service plus admin-override mutators for the outpost catalogue. The records themselves are
 * owned by {@link UexUniverseSyncService}; this service only exposes the read API and the
 * admin-only {@code hasLoadingDock} pin used by the UEX-overrides admin page.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutpostService {

  private final OutpostRepository outpostRepository;

  /**
   * Returns the paged outpost catalogue.
   *
   * @param pageable page request
   * @return one page of outposts, sorted by the pageable's sort
   */
  public Page<Outpost> getAllOutposts(Pageable pageable) {
    return outpostRepository.findAll(pageable);
  }

  /**
   * Returns a single outpost by primary key.
   *
   * @param id outpost primary key
   * @return the managed outpost entity
   * @throws NotFoundException when no outpost matches the id
   */
  public Outpost getOutpost(UUID id) {
    return outpostRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Outpost not found"));
  }

  /**
   * Pins {@code hasLoadingDock} to the supplied value and marks the row as admin-overridden so the
   * next UEX sweep leaves the value column untouched.
   *
   * @param id outpost primary key
   * @param value desired {@code hasLoadingDock} value
   * @return the persisted outpost
   */
  @Transactional
  public Outpost setLoadingDockOverride(UUID id, boolean value) {
    Outpost outpost = getOutpost(id);
    outpost.setHasLoadingDock(value);
    outpost.setHasLoadingDockOverridden(true);
    return outpostRepository.save(outpost);
  }

  /**
   * Releases the admin pin on {@code hasLoadingDock}. The value column stays at its last value
   * until the next UEX sweep overwrites it from the upstream feed.
   *
   * @param id outpost primary key
   * @return the persisted outpost
   */
  @Transactional
  public Outpost clearLoadingDockOverride(UUID id) {
    Outpost outpost = getOutpost(id);
    outpost.setHasLoadingDockOverridden(false);
    return outpostRepository.save(outpost);
  }
}
