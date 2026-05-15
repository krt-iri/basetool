package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Stores the evaluation level assigned to a member (identified by JWT {@code sub}) for a specific
 * {@link PromotionCategory}. {@link #assignedLevel} may be {@code null} to indicate that no level
 * has been assigned yet.
 */
@Entity
@Table(name = "member_evaluation")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberEvaluation extends AbstractEntity<UUID> {

  // {@code onMethod_ = @__(@Override)} tells Lombok to attach a real {@code @Override} to the
  // generated {@code getId()} so CodeQL recognises this method as the {@code Persistable.getId()}
  // implementation.
  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  // Excluded from {@code @ToString} because the LAZY parent association would either trigger a
  // LazyInitializationException outside a Hibernate session or recurse back through
  // category.levelContents and the topic's reverse children.
  @ToString.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private PromotionCategory category;

  @Enumerated(EnumType.STRING)
  @Column(name = "assigned_level", length = 10)
  private PromotionLevel assignedLevel;
}
