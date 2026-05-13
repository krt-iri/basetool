package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.AnnouncementMapper;
import de.greluc.krt.iri.basetool.backend.model.dto.AnnouncementDto;
import de.greluc.krt.iri.basetool.backend.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Public/admin REST surface over the single shared announcement. The {@code GET} root path is
 * public (drives the home-page banner); {@code GET /admin} returns the record even when content is
 * blank (admins reuse the same row); PUT/DELETE are ADMIN/OFFICER only.
 */
@RestController
@RequestMapping("/api/v1/announcement")
@RequiredArgsConstructor
@Transactional
public class AnnouncementController {

  private final AnnouncementService announcementService;
  private final AnnouncementMapper announcementMapper;

  /**
   * Returns the currently active (non-blank content) announcement, or 204 when none is active. 204
   * is intentional — frontends rely on it to hide the announcement banner entirely.
   *
   * @return announcement DTO with 200, or 204 when none active
   */
  @GetMapping
  public ResponseEntity<AnnouncementDto> getPublicAnnouncement() {
    return announcementService
        .getPublicAnnouncement()
        .map(announcementMapper::toDto)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.noContent().build());
  }

  /**
   * Admin-view announcement — returns the existing row even when blank so the edit form pre-fills
   * with the last saved content instead of forcing the admin to start over.
   *
   * @return the announcement DTO
   */
  @GetMapping("/admin")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public AnnouncementDto getAdminAnnouncement() {
    return announcementMapper.toDto(announcementService.getAdminAnnouncement());
  }

  /**
   * Updates the shared announcement with optimistic-lock check.
   *
   * @param request new content + expected version
   * @return the persisted DTO
   */
  @PutMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public AnnouncementDto updateAnnouncement(
      @RequestBody @jakarta.validation.Valid AnnouncementRequest request) {
    return announcementMapper.toDto(
        announcementService.updateAnnouncement(request.getContent(), request.getVersion()));
  }

  /** Removes the announcement entirely. Next PUT creates a fresh row. */
  @DeleteMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public void deleteAnnouncement() {
    announcementService.deleteAnnouncement();
  }

  /** Request body for {@link #updateAnnouncement}. */
  @lombok.Data
  public static class AnnouncementRequest {
    @jakarta.validation.constraints.NotBlank private String content;
    @jakarta.validation.constraints.NotNull private Long version;
  }
}
