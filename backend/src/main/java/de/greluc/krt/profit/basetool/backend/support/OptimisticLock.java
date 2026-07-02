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

package de.greluc.krt.profit.basetool.backend.support;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Optimistic-lock version-check helper family for the hand-rolled {@code @Version} mismatch guard
 * that pervades the service layer (S2, part of #905).
 *
 * <p>Replaces the ~29 copy-pasted {@code if (entity.getVersion() != null &&
 * !entity.getVersion().equals(clientVersion)) throw new ObjectOptimisticLockingFailureException(…)}
 * blocks — which had drifted into five subtly different null/equality shapes — with one named
 * method per shape, so a maintainer picks the semantic explicitly instead of re-deriving the
 * null-guards. Each method throws Spring's {@link ObjectOptimisticLockingFailureException} (which
 * {@code GlobalExceptionHandler} maps to an HTTP 409, code {@code OPTIMISTIC_LOCK}); it lives in
 * the dependency-leaf {@code support} package because it depends only on the entity {@link Class}
 * and identifier passed in, never on {@code model} / {@code service} (ADR-0047).
 *
 * <p>The {@code identifier} is used only to build the 409 exception (its message names the entity +
 * id) and may be {@code null}, a plain id, or a composite-key object — it is never dereferenced.
 *
 * <p><b>Not for the manual {@code Mission} counters.</b> {@code Mission}'s {@code coreVersion} /
 * {@code scheduleVersion} / {@code flagsVersion} / {@code partyLeadVersion} / {@code stepsVersion}
 * / {@code objectivesVersion} / {@code owningOrgUnitVersion} are plain business {@code Long}s (NOT
 * JPA {@code @Version}) that back the per-section fine-grained lock (REQ-ORG-018); they go through
 * {@link MissionSectionVersions} (whose {@code null -> 0L} semantics differ) and must not be routed
 * through this helper.
 */
public final class OptimisticLock {

  /** Non-instantiable static-helper holder. */
  private OptimisticLock() {}

  /**
   * The dominant shape: passes when the persisted version is absent (entity not yet versioned),
   * otherwise raises 409 unless the client version equals it. A {@code null} {@code clientVersion}
   * is treated as a mismatch (so an omitted version 409s) — use {@link #checkOptionalClient} when a
   * missing client version should instead skip the check.
   *
   * @param persistedVersion the entity's current {@code @Version}, or {@code null} if unversioned
   * @param clientVersion the version the caller last saw (echoed from the write DTO)
   * @param entityType the entity class, for the 409 exception's message
   * @param identifier the entity id (or composite-key object), for the 409 exception; may be {@code
   *     null}
   * @throws ObjectOptimisticLockingFailureException if the persisted version is present and differs
   */
  public static void check(
      @Nullable Long persistedVersion,
      @Nullable Long clientVersion,
      @NotNull Class<?> entityType,
      @Nullable Object identifier) {
    if (persistedVersion != null && !persistedVersion.equals(clientVersion)) {
      throw conflict(entityType, identifier);
    }
  }

  /**
   * Admin skip-on-null-client shape: passes when the client omits the version ({@code clientVersion
   * == null}) or the entity is unversioned, otherwise raises 409 unless they match. Used by the
   * flows that let a privileged caller force-save without echoing a version (user-admin,
   * announcements).
   *
   * @param persistedVersion the entity's current {@code @Version}, or {@code null} if unversioned
   * @param clientVersion the caller's last-seen version, or {@code null} to deliberately skip the
   *     check
   * @param entityType the entity class, for the 409 exception's message
   * @param identifier the entity id (or composite-key object), for the 409 exception; may be {@code
   *     null}
   * @throws ObjectOptimisticLockingFailureException if both versions are present and differ
   */
  public static void checkOptionalClient(
      @Nullable Long persistedVersion,
      @Nullable Long clientVersion,
      @NotNull Class<?> entityType,
      @Nullable Object identifier) {
    if (clientVersion != null
        && persistedVersion != null
        && !persistedVersion.equals(clientVersion)) {
      throw conflict(entityType, identifier);
    }
  }

  /**
   * Strict primitive-{@code long} shape that additionally treats an unversioned entity as a
   * conflict: raises 409 when the persisted version is absent OR does not equal {@code
   * clientVersion}. Used where a booking request must already carry a version before it can be
   * decided.
   *
   * @param persistedVersion the entity's current {@code @Version}, or {@code null} if unversioned
   *     (which is itself a conflict here)
   * @param clientVersion the caller's last-seen version (primitive, never absent)
   * @param entityType the entity class, for the 409 exception's message
   * @param identifier the entity id (or composite-key object), for the 409 exception; may be {@code
   *     null}
   * @throws ObjectOptimisticLockingFailureException if the persisted version is absent or differs
   */
  public static void checkRequired(
      @Nullable Long persistedVersion,
      long clientVersion,
      @NotNull Class<?> entityType,
      @Nullable Object identifier) {
    if (persistedVersion == null || persistedVersion != clientVersion) {
      throw conflict(entityType, identifier);
    }
  }

  /**
   * Builds the 409 exception carrying the entity type and identifier, mirroring the hand-written
   * {@code new ObjectOptimisticLockingFailureException(Entity.class, id)} the call sites used.
   *
   * @param entityType the entity class
   * @param identifier the entity id (or composite-key object); may be {@code null}
   * @return the conflict exception to throw
   */
  @NotNull
  private static ObjectOptimisticLockingFailureException conflict(
      @NotNull Class<?> entityType, @Nullable Object identifier) {
    return new ObjectOptimisticLockingFailureException(entityType, identifier);
  }
}
