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

package de.greluc.krt.profit.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The raw uploaded catalog bytes for one {@link P4kImportJob}, split into a 1:1 side table keyed by
 * the job id (= {@link #jobId}, not generated) so that listing / polling jobs never drags the
 * multi-MB upload through the persistence context. Written once when the job is enqueued and read
 * once by the worker; the {@code job_id} foreign key is {@code ON DELETE CASCADE}, so pruning a job
 * drops its payload with it.
 */
@Entity
@Table(name = "p4k_import_job_payload")
@Getter
@Setter
@NoArgsConstructor
public class P4kImportJobPayload {

  /** Primary key and foreign key: the owning {@link P4kImportJob}'s id. */
  @Id
  @Column(name = "job_id")
  private UUID jobId;

  /** The uploaded P4K catalog JSON, stored verbatim as {@code bytea}. */
  @Column(name = "content", nullable = false)
  private byte[] content;
}
