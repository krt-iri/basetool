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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.P4kImportJob;
import de.greluc.krt.iri.basetool.backend.model.P4kImportJobKind;
import de.greluc.krt.iri.basetool.backend.model.P4kImportJobPayload;
import de.greluc.krt.iri.basetool.backend.model.P4kImportJobStatus;
import de.greluc.krt.iri.basetool.backend.repository.P4kImportJobPayloadRepository;
import de.greluc.krt.iri.basetool.backend.repository.P4kImportJobRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure-Mockito unit tests for {@link P4kImportJobService}: the two repositories are mocked, no Spring
 * context and no database. {@code jobRepository.save} is stubbed to assign an id, standing in for the
 * Hibernate {@code GenerationType.UUID} generator, so the create paths can wire the payload to the
 * persisted job id. Covers job creation (preview + apply-from-preview with its guards), the lifecycle
 * transitions, payload reclaim, prune delegation and the startup orphan reconciliation.
 */
@ExtendWith(MockitoExtension.class)
class P4kImportJobServiceTest {

  @Mock private P4kImportJobRepository jobRepository;
  @Mock private P4kImportJobPayloadRepository payloadRepository;

  @InjectMocks private P4kImportJobService service;

  /** Stubs {@code save} to assign a random id (mimicking the JPA UUID generator) and echo the job. */
  private void stubSaveAssignsId() {
    when(jobRepository.save(any(P4kImportJob.class)))
        .thenAnswer(
            invocation -> {
              P4kImportJob job = invocation.getArgument(0);
              if (job.getId() == null) {
                job.setId(UUID.randomUUID());
              }
              return job;
            });
  }

  private P4kImportJob jobWithId(UUID id, P4kImportJobStatus status) {
    P4kImportJob job = new P4kImportJob();
    job.setId(id);
    job.setKind(P4kImportJobKind.PREVIEW);
    job.setStatus(status);
    return job;
  }

  // ────────────────────────────────────────────────────────── create preview ──

  @Test
  void createPreviewJob_persistsPendingJobAndPayload() {
    stubSaveAssignsId();
    byte[] bytes = "{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
    UUID user = UUID.randomUUID();

    P4kImportJob job = service.createPreviewJob(bytes, "p4k.json", user);

    assertEquals(P4kImportJobKind.PREVIEW, job.getKind());
    assertEquals(P4kImportJobStatus.PENDING, job.getStatus());
    assertEquals(user, job.getCreatedBy());
    assertEquals("p4k.json", job.getSourceFilename());
    assertEquals((long) bytes.length, job.getFileSizeBytes());
    assertNotNull(job.getId());

    ArgumentCaptor<P4kImportJobPayload> payload = ArgumentCaptor.forClass(P4kImportJobPayload.class);
    verify(payloadRepository).save(payload.capture());
    assertEquals(job.getId(), payload.getValue().getJobId());
    assertArrayEquals(bytes, payload.getValue().getContent());
  }

  @Test
  void createPreviewJob_emptyBytes_throwsBadRequestAndPersistsNothing() {
    assertThrows(
        BadRequestException.class,
        () -> service.createPreviewJob(new byte[0], "p4k.json", UUID.randomUUID()));
    verify(jobRepository, never()).save(any());
    verify(payloadRepository, never()).save(any());
  }

  // ──────────────────────────────────────────────────────── create apply ──

  @Test
  void createApplyJob_fromSucceededPreview_copiesPayloadAndLinksPreview() {
    stubSaveAssignsId();
    UUID previewId = UUID.randomUUID();
    UUID user = UUID.randomUUID();
    P4kImportJob preview = jobWithId(previewId, P4kImportJobStatus.SUCCEEDED);
    preview.setSourceFilename("p4k.json");
    preview.setFileSizeBytes(4096L);
    when(jobRepository.findById(previewId)).thenReturn(Optional.of(preview));
    when(payloadRepository.copyPayload(any(UUID.class), eq(previewId))).thenReturn(1);

    P4kImportJob apply = service.createApplyJob(previewId, true, user);

    assertEquals(P4kImportJobKind.APPLY, apply.getKind());
    assertEquals(P4kImportJobStatus.PENDING, apply.getStatus());
    assertTrue(apply.isSeedNew());
    assertEquals(previewId, apply.getPreviewJobId());
    assertEquals("p4k.json", apply.getSourceFilename());
    assertEquals(4096L, apply.getFileSizeBytes());
    verify(payloadRepository).copyPayload(apply.getId(), previewId);
  }

  @Test
  void createApplyJob_unknownJob_throwsNotFound() {
    UUID id = UUID.randomUUID();
    when(jobRepository.findById(id)).thenReturn(Optional.empty());
    assertThrows(NotFoundException.class, () -> service.createApplyJob(id, false, UUID.randomUUID()));
    verify(payloadRepository, never()).copyPayload(any(), any());
  }

  @Test
  void createApplyJob_referencingAnApplyJob_throwsBadRequest() {
    UUID id = UUID.randomUUID();
    P4kImportJob applyJob = jobWithId(id, P4kImportJobStatus.SUCCEEDED);
    applyJob.setKind(P4kImportJobKind.APPLY);
    when(jobRepository.findById(id)).thenReturn(Optional.of(applyJob));
    assertThrows(
        BadRequestException.class, () -> service.createApplyJob(id, false, UUID.randomUUID()));
  }

  @Test
  void createApplyJob_previewNotYetSucceeded_throwsBadRequest() {
    UUID id = UUID.randomUUID();
    when(jobRepository.findById(id))
        .thenReturn(Optional.of(jobWithId(id, P4kImportJobStatus.RUNNING)));
    assertThrows(
        BadRequestException.class, () -> service.createApplyJob(id, false, UUID.randomUUID()));
  }

  @Test
  void createApplyJob_payloadAlreadyGone_throwsBadRequest() {
    stubSaveAssignsId();
    UUID previewId = UUID.randomUUID();
    when(jobRepository.findById(previewId))
        .thenReturn(Optional.of(jobWithId(previewId, P4kImportJobStatus.SUCCEEDED)));
    when(payloadRepository.copyPayload(any(UUID.class), eq(previewId))).thenReturn(0);
    assertThrows(
        BadRequestException.class, () -> service.createApplyJob(previewId, false, UUID.randomUUID()));
  }

  // ──────────────────────────────────────────────────── lifecycle transitions ──

  @Test
  void markRunning_setsStatusAndStartedAt() {
    UUID id = UUID.randomUUID();
    P4kImportJob job = jobWithId(id, P4kImportJobStatus.PENDING);
    when(jobRepository.findById(id)).thenReturn(Optional.of(job));

    service.markRunning(id);

    assertEquals(P4kImportJobStatus.RUNNING, job.getStatus());
    assertNotNull(job.getStartedAt());
  }

  @Test
  void markSucceeded_storesResultAndFinishedAt() {
    UUID id = UUID.randomUUID();
    P4kImportJob job = jobWithId(id, P4kImportJobStatus.RUNNING);
    when(jobRepository.findById(id)).thenReturn(Optional.of(job));

    service.markSucceeded(id, "{\"dryRun\":true}");

    assertEquals(P4kImportJobStatus.SUCCEEDED, job.getStatus());
    assertEquals("{\"dryRun\":true}", job.getResultJson());
    assertNotNull(job.getFinishedAt());
    assertNull(job.getErrorMessage());
  }

  @Test
  void markFailed_storesErrorAndFinishedAt() {
    UUID id = UUID.randomUUID();
    P4kImportJob job = jobWithId(id, P4kImportJobStatus.RUNNING);
    when(jobRepository.findById(id)).thenReturn(Optional.of(job));

    service.markFailed(id, "boom");

    assertEquals(P4kImportJobStatus.FAILED, job.getStatus());
    assertEquals("boom", job.getErrorMessage());
    assertNotNull(job.getFinishedAt());
    assertNull(job.getResultJson());
  }

  // ──────────────────────────────────────────────────── payload + prune ──

  @Test
  void deletePayload_deletesOnlyWhenPresent() {
    UUID id = UUID.randomUUID();
    when(payloadRepository.existsById(id)).thenReturn(true);
    service.deletePayload(id);
    verify(payloadRepository).deleteById(id);
  }

  @Test
  void deletePayload_isNoOpWhenAlreadyGone() {
    UUID id = UUID.randomUUID();
    when(payloadRepository.existsById(id)).thenReturn(false);
    service.deletePayload(id);
    verify(payloadRepository, never()).deleteById(any());
  }

  @Test
  void pruneOldJobs_delegatesToRepository() {
    when(jobRepository.deleteByCreatedAtBefore(any())).thenReturn(3);
    assertEquals(3, service.pruneOldJobs());
  }

  // ──────────────────────────────────────────────────── startup reconcile ──

  @Test
  void failOrphanedJobs_flipsPendingAndRunningToFailed() {
    P4kImportJob pending = jobWithId(UUID.randomUUID(), P4kImportJobStatus.PENDING);
    P4kImportJob running = jobWithId(UUID.randomUUID(), P4kImportJobStatus.RUNNING);
    when(jobRepository.findByStatusIn(any())).thenReturn(List.of(pending, running));

    service.failOrphanedJobs();

    assertEquals(P4kImportJobStatus.FAILED, pending.getStatus());
    assertEquals(P4kImportJobStatus.FAILED, running.getStatus());
    assertNotNull(pending.getFinishedAt());
    assertNotNull(running.getErrorMessage());
  }

  @Test
  void failOrphanedJobs_withNoOrphans_doesNothing() {
    when(jobRepository.findByStatusIn(any())).thenReturn(List.of());
    service.failOrphanedJobs();
    verify(jobRepository).findByStatusIn(any());
  }
}
