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

package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.P4kImportJobKind;
import de.greluc.krt.iri.basetool.backend.model.P4kImportJobStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Boundary view of one asynchronous P4K catalog-import run, returned by the admin import endpoints
 * and polled by the page. While {@link #status} is {@code PENDING} / {@code RUNNING} the {@link
 * #result} is {@code null}; on {@code SUCCEEDED} it carries the per-type {@link
 * P4kImportResultDto}; on {@code FAILED} the {@link #errorMessage} explains why.
 *
 * @param id the job id
 * @param kind whether the run previews (dry) or applies the catalog
 * @param status the lifecycle status
 * @param seedNew for an APPLY run, whether brand-new unmatched rows are seeded
 * @param sourceFilename original upload filename for display, or {@code null}
 * @param fileSizeBytes size of the uploaded catalog in bytes, or {@code null}
 * @param previewJobId for an APPLY run, the preview it was launched from, else {@code null}
 * @param result the per-type reconciliation result once {@code SUCCEEDED}, else {@code null}
 * @param errorMessage the failure reason once {@code FAILED}, else {@code null}
 * @param createdAt when the run was enqueued
 * @param startedAt when the worker began (set on {@code RUNNING}), or {@code null}
 * @param finishedAt when the worker finished (set on {@code SUCCEEDED} / {@code FAILED}), or {@code
 *     null}
 */
public record P4kImportJobDto(
    UUID id,
    P4kImportJobKind kind,
    P4kImportJobStatus status,
    boolean seedNew,
    String sourceFilename,
    Long fileSizeBytes,
    UUID previewJobId,
    P4kImportResultDto result,
    String errorMessage,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt) {}
