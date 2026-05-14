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

/**
 * Defines the requirements for a promotion from one rank to another. Both {@link #topic} and {@link
 * #category} are optional; at least one should be set to give the requirement a meaningful scope.
 */
@Entity
@Table(name = "rank_requirement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RankRequirement extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "from_rank", nullable = false)
  private int fromRank;

  @Column(name = "to_rank", nullable = false)
  private int toRank;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "topic_id")
  private PromotionTopic topic;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  private PromotionCategory category;

  @Enumerated(EnumType.STRING)
  @Column(name = "minimum_level", nullable = false, length = 10)
  private PromotionLevel minimumLevel;

  @Column(name = "required_count", nullable = false)
  private int requiredCount;

  @Column(length = 2000)
  private String description;
}
