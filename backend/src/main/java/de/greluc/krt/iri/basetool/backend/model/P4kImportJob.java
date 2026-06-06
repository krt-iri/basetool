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

package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One asynchronous KRT P4K Reader catalog-import run. The upload enqueues a {@link
 * P4kImportJobKind#PREVIEW PREVIEW} job ({@link P4kImportJobStatus#PENDING PENDING}); a
 * single-thread {@code @Async} worker advances it to {@link P4kImportJobStatus#RUNNING RUNNING},
 * parses the stored catalog and reconciles it against the master data, then writes the serialized
 * {@code P4kImportResultDto} into {@link #resultJson} and flips to {@link
 * P4kImportJobStatus#SUCCEEDED SUCCEEDED} (or stores an {@link #errorMessage} and flips to {@link
 * P4kImportJobStatus#FAILED FAILED}). A finished preview can be launched as a second {@link
 * P4kImportJobKind#APPLY APPLY} job, which carries {@link #previewJobId} back to its origin and
 * runs against its own copy of the upload.
 *
 * <p>The multi-MB upload itself lives in the 1:1 {@link P4kImportJobPayload} side table (keyed by
 * this job's id), never mapped here, so listing / polling jobs stays cheap. Extends {@link
 * AbstractEntity} for the optimistic-lock {@code version} and the {@code created_at} / {@code
 * updated_at} audit timestamps.
 */
@Entity
@Table(name = "p4k_import_job")
@Getter
@Setter
@NoArgsConstructor
public class P4kImportJob extends AbstractEntity<UUID> {

  /** Surrogate primary key, assigned by Hibernate before insert. */
  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** Whether this run previews (dry) or applies the catalog. */
  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 16)
  private P4kImportJobKind kind;

  /** Lifecycle status, advanced by the import worker. */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private P4kImportJobStatus status;

  /** For an APPLY run: whether brand-new unmatched player-facing rows are seeded. */
  @Column(name = "seed_new", nullable = false)
  private boolean seedNew;

  /** Original upload filename, for display in the job list (forensic; may be {@code null}). */
  @Column(name = "source_filename", length = 255)
  private String sourceFilename;

  /** Size of the uploaded catalog in bytes, for display (may be {@code null}). */
  @Column(name = "file_size_bytes")
  private Long fileSizeBytes;

  /**
   * The serialized {@code P4kImportResultDto} (plain JSON text) once the run succeeds, or {@code
   * null} while pending / running / on failure. Only ever stored and echoed back to the page, never
   * queried into, hence {@code TEXT} rather than {@code jsonb}.
   */
  @Column(name = "result_json", columnDefinition = "TEXT")
  private String resultJson;

  /** Human-readable failure reason when {@link #status} is {@code FAILED}, else {@code null}. */
  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  /**
   * For an APPLY run, the PREVIEW job it was launched from (informational link; the APPLY copies
   * the upload into its own payload row so it does not depend on the preview's payload surviving).
   * {@code null} for a PREVIEW run.
   */
  @Column(name = "preview_job_id")
  private UUID previewJobId;

  /** JWT {@code sub} of the administrator who enqueued the run. */
  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  /** When the worker picked the job up (set on {@code RUNNING}), or {@code null}. */
  @Column(name = "started_at")
  private Instant startedAt;

  /** When the worker finished (set on {@code SUCCEEDED} / {@code FAILED}), or {@code null}. */
  @Column(name = "finished_at")
  private Instant finishedAt;
}
