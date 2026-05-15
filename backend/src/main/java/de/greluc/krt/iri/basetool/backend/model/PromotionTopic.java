package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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

/** Top-level grouping entity for the promotion system (e.g. "Grundlagen", "Spezialisierungen"). */
@Entity
@Table(name = "promotion_topic")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionTopic extends AbstractEntity<UUID> {

  // {@code onMethod_ = @__(@Override)} tells Lombok to attach a real {@code @Override} to the
  // generated {@code getId()} so it is visibly tagged as the implementation of
  // {@code Persistable.getId()} (CodeQL flags missing override annotations on interface
  // implementations). The field-level {@code @Getter} wins over the class-level one for this
  // field so the override marker is attached without disabling Lombok for the rest of the class.
  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 2000)
  private String description;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  // Excluded from {@code @ToString} because {@code List<PromotionCategory>} is a LAZY association
  // and the children's own {@code toString()} would either trigger a LazyInitializationException
  // outside a Hibernate session or recurse back into this topic.
  @ToString.Exclude
  @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sortOrder ASC")
  @Builder.Default
  private List<PromotionCategory> categories = new ArrayList<>();
}
