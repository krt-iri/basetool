package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.Announcement;
import de.greluc.krt.iri.basetool.backend.model.dto.AnnouncementDto;
import de.greluc.krt.iri.basetool.backend.mapper.AnnouncementMapper;
import de.greluc.krt.iri.basetool.backend.service.AnnouncementService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/announcement")
@RequiredArgsConstructor
@Transactional
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final AnnouncementMapper announcementMapper;

    @GetMapping
    public ResponseEntity<AnnouncementDto> getPublicAnnouncement() {
        return announcementService.getPublicAnnouncement()
                .map(announcementMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public AnnouncementDto getAdminAnnouncement() {
        return announcementMapper.toDto(announcementService.getAdminAnnouncement());
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public AnnouncementDto updateAnnouncement(@RequestBody @jakarta.validation.Valid AnnouncementRequest request) {
        return announcementMapper.toDto(announcementService.updateAnnouncement(request.getContent(), request.getVersion()));
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public void deleteAnnouncement() {
        announcementService.deleteAnnouncement();
    }

    @lombok.Data
    public static class AnnouncementRequest {
        @jakarta.validation.constraints.NotBlank
        private String content;
        @jakarta.validation.constraints.NotNull
        private Long version;
    }
}
