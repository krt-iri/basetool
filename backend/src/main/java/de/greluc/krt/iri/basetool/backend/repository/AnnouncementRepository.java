package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {
    
    // Fetch the single "current" announcement. 
    // We assume there's basically one main announcement record that we update, 
    // or if we have multiple, we fetch the most recently updated one.
    // The requirement says "ein informationsfeld". So let's stick to "findTopByOrderByUpdatedAtDesc"
    Optional<Announcement> findTopByOrderByUpdatedAtDesc();
}
