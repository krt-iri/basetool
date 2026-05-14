package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Announcement JPA entity.
 *
 * <p>The {@code updatedAt} column lives on {@link AbstractEntity} where {@code @UpdateTimestamp}
 * keeps it fresh on every {@code persist}/{@code update}. Earlier revisions shadowed that field
 * here with a manual {@code @PrePersist}/{@code @PreUpdate} hook — the duplicate has been removed
 * to avoid the JPA column-mapping ambiguity and to silence the CodeQL "missing {@code @Override} on
 * {@code getUpdatedAt}" finding that the shadowing produced.
 */
@Entity
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Announcement extends AbstractEntity<UUID> {

  // {@code onMethod_ = @__(@Override)} tells Lombok to attach a real {@code @Override} to the
  // generated {@code getId()} — required because the method implements {@code Persistable.getId()}
  // and CodeQL flags missing override annotations on interface implementations.
  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Getter
  @Column(columnDefinition = "TEXT")
  private String content;
}
