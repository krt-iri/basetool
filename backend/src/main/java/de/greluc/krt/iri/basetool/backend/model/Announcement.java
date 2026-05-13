package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/** Announcement JPA entity. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Announcement extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(columnDefinition = "TEXT")
  private String content;

  private Instant updatedAt;

  /** JPA lifecycle hook that refreshes {@code updatedAt} on every insert and update. */
  @PrePersist
  @PreUpdate
  public void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
