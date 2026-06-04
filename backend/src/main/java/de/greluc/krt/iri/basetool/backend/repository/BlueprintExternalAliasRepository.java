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

import de.greluc.krt.iri.basetool.backend.model.BlueprintExternalAlias;
import de.greluc.krt.iri.basetool.backend.model.BlueprintExternalAliasSource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link BlueprintExternalAlias}. Read/write goes through the service
 * layer (no direct controller injection — pinned by the {@code
 * controllerLayerShouldNotDependOnRepositoryLayer} ArchUnit rule).
 */
@Repository
public interface BlueprintExternalAliasRepository
    extends JpaRepository<BlueprintExternalAlias, UUID> {

  /**
   * Lookup used by the personal-blueprint import resolution chain: external systems sometimes carry
   * the same name with different casing across patch versions, so this match tolerates the drift.
   *
   * @param sourceSystem catalogue the alias belongs to
   * @param externalName case-insensitive external blueprint name
   * @return the alias if present, empty otherwise
   */
  Optional<BlueprintExternalAlias> findBySourceSystemAndExternalNameIgnoreCase(
      BlueprintExternalAliasSource sourceSystem, String externalName);

  /**
   * Strict pre-create duplicate check used by the service layer to emit a 409 before the DB unique
   * constraint fires. Case-sensitive because the unique constraint is exact-match.
   *
   * @param sourceSystem catalogue the alias belongs to
   * @param externalName exact external blueprint name
   * @return the alias if present, empty otherwise
   */
  Optional<BlueprintExternalAlias> findBySourceSystemAndExternalName(
      BlueprintExternalAliasSource sourceSystem, String externalName);
}
