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

package de.greluc.krt.profit.basetool.frontend.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Frontend mirror of the backend {@code P4kImportJobDto} returned by the async P4K import job
 * endpoints ({@code /api/v1/admin/import/p4k/jobs[/{id}][/apply]}). The page polls these: while
 * {@link #status} is {@code PENDING} / {@code RUNNING} the {@link #result} is {@code null}; on
 * {@code SUCCEEDED} it carries the per-type {@link P4kImportResultDto}; on {@code FAILED} the
 * {@link #errorMessage} explains why.
 *
 * <p>Jackson-bindable (camelCase matching the backend JSON); {@code @JsonIgnoreProperties} keeps
 * the frontend resilient if the backend grows the payload. Ids, the {@code kind} / {@code status}
 * enums and the timestamps are carried as plain strings; the page only displays / compares them.
 *
 * @param id the job id
 * @param kind {@code PREVIEW} or {@code APPLY}
 * @param status {@code PENDING} / {@code RUNNING} / {@code SUCCEEDED} / {@code FAILED}
 * @param seedNew for an APPLY run, whether brand-new unmatched rows are seeded
 * @param sourceFilename original upload filename for display, or {@code null}
 * @param fileSizeBytes size of the uploaded catalog in bytes, or {@code null}
 * @param previewJobId for an APPLY run, the preview it was launched from, else {@code null}
 * @param result the per-type reconciliation result once {@code SUCCEEDED}, else {@code null}
 * @param errorMessage the failure reason once {@code FAILED}, else {@code null}
 * @param createdAt ISO-8601 instant the run was enqueued
 * @param startedAt ISO-8601 instant the worker began, or {@code null}
 * @param finishedAt ISO-8601 instant the worker finished, or {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record P4kImportJobDto(
    String id,
    String kind,
    String status,
    boolean seedNew,
    String sourceFilename,
    Long fileSizeBytes,
    String previewJobId,
    P4kImportResultDto result,
    String errorMessage,
    String createdAt,
    String startedAt,
    String finishedAt) {}
