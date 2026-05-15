package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Evaluation category belonging to a {@link PromotionTopic} (e.g. "Flug Kenntnisse", "KG Mining:
 * Themengebiet Mining").
 */
@Entity
@Table(name = "promotion_category")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionCategory extends AbstractEntity<UUID> {

  // {@code onMethod_ = @__(@Override)} tells Lombok to attach a real {@code @Override} to the
  // generated {@code getId()} so CodeQL recognises this method as the {@code Persistable.getId()}
  // implementation.
  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  // Excluded from {@code @ToString} because the LAZY back-reference would either trigger a
  // LazyInitializationException outside a Hibernate session or recurse topic -> categories ->
  // topic.
  @ToString.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "topic_id", nullable = false)
  private PromotionTopic topic;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 2000)
  private String description;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  // Excluded from {@code @ToString} for the same lazy/recursion reasons as {@link #topic}.
  @ToString.Exclude
  @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<PromotionLevelContent> levelContents = new ArrayList<>();
}
