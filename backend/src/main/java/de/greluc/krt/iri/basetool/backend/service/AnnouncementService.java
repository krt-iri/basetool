package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Announcement;
import de.greluc.krt.iri.basetool.backend.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    public Optional<Announcement> getPublicAnnouncement() {
        return announcementRepository.findAll().stream()
                .filter(a -> a.getContent() != null && !a.getContent().isBlank())
                .max(Comparator.comparing(Announcement::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    public Announcement getAdminAnnouncement() {
        // Try to find the latest active one first to avoid creating duplicates or editing old hidden ones if a newer one exists
        Optional<Announcement> active = getPublicAnnouncement();
        if (active.isPresent()) {
            return active.get();
        }

        // If no active one, try to find ANY latest entry to reuse (even if content is empty/null)
        return announcementRepository.findAll().stream()
                .max(Comparator.comparing(Announcement::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElseGet(() -> announcementRepository.save(new Announcement()));
    }

    @Transactional
    public Announcement updateAnnouncement(@NotNull String content, @Nullable Long version) {
        Announcement announcement = getAdminAnnouncement();
        if (version != null && announcement.getVersion() != null && !announcement.getVersion().equals(version)) {
            throw new ObjectOptimisticLockingFailureException(Announcement.class, announcement.getId());
        }
        announcement.setContent(content);
        return announcementRepository.save(announcement);
    }

    @Transactional
    public void deleteAnnouncement() {
        announcementRepository.deleteAll();
    }
}
