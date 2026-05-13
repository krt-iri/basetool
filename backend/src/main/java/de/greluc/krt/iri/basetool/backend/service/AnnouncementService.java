package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Announcement;
import de.greluc.krt.iri.basetool.backend.repository.AnnouncementRepository;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {

  private final AnnouncementRepository announcementRepository;

  public Optional<Announcement> getPublicAnnouncement() {
    return announcementRepository.findAll().stream()
        .filter(a -> a.getContent() != null && !a.getContent().isBlank())
        .max(
            Comparator.comparing(
                Announcement::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
  }

  public Announcement getAdminAnnouncement() {
    // Try the latest active announcement first; fall back to the latest entry
    // overall (even if its content is empty/null) so admins reuse the existing
    // row instead of accumulating duplicates. As a last resort create one.
    return getPublicAnnouncement()
        .orElseGet(
            () ->
                announcementRepository.findAll().stream()
                    .max(
                        Comparator.comparing(
                            Announcement::getUpdatedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElseGet(() -> announcementRepository.save(new Announcement())));
  }

  @Transactional
  public Announcement updateAnnouncement(@NotNull String content, @Nullable Long version) {
    Announcement announcement = getAdminAnnouncement();
    if (version != null
        && announcement.getVersion() != null
        && !announcement.getVersion().equals(version)) {
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
