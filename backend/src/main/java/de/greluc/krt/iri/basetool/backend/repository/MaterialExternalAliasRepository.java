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

import de.greluc.krt.iri.basetool.backend.model.MaterialExternalAlias;
import de.greluc.krt.iri.basetool.backend.model.MaterialExternalAliasSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link MaterialExternalAlias}. Read access is sorted by external name
 * for stable rendering in the admin page; write access goes through the service layer (no direct
 * controller injection — pinned by the {@code controllerLayerShouldNotDependOnRepositoryLayer}
 * ArchUnit rule).
 */
@Repository
public interface MaterialExternalAliasRepository
    extends JpaRepository<MaterialExternalAlias, UUID> {

  /**
   * Returns every alias sorted by external name. The stored casing is preserved for display, but
   * uniqueness is case-insensitive (V146 index on {@code (source_system, LOWER(external_name))}),
   * so the table view never shows two case-variants of the same name.
   *
   * @return all alias rows ordered by external_name ascending
   */
  List<MaterialExternalAlias> findAllByOrderByExternalNameAsc();

  /**
   * Lookup used by the R3 Wiki commodity sync's resolution chain step 2 and as the service-layer
   * pre-insert duplicate check. Case-insensitive by convention: external systems sometimes carry
   * the same name with different casing across patch versions, so the lookup tolerates the drift.
   * At most one row can match — the V146 unique index on {@code (source_system,
   * LOWER(external_name))} folds case exactly like this query does (REQ-REFINERY-010).
   *
   * @param sourceSystem catalogue the alias belongs to
   * @param externalName case-insensitive external commodity name
   * @return the alias if present, empty otherwise
   */
  Optional<MaterialExternalAlias> findBySourceSystemAndExternalNameIgnoreCase(
      MaterialExternalAliasSource sourceSystem, String externalName);
}
