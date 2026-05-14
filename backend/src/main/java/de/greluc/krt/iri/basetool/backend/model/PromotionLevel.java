package de.greluc.krt.iri.basetool.backend.model;

import org.jetbrains.annotations.NotNull;

/**
 * Three-step evaluation grade used throughout the promotion system.
 *
 * <p>Ordering follows the squadron's promotion concept: {@link #LEVEL_A} is the entry grade (basic
 * competence), {@link #LEVEL_B} is the intermediate grade and {@link #LEVEL_C} is the expert grade.
 * Higher grades therefore include all lower ones for the purpose of {@link RankRequirement}
 * evaluation – a member assigned {@link #LEVEL_C} in a category automatically satisfies an "at
 * least {@link #LEVEL_A}" check for that category.
 */
public enum PromotionLevel {
  /** Entry level – basic competence. Lowest grade in the ordering. */
  LEVEL_A,
  /** Intermediate level – broadened competence. */
  LEVEL_B,
  /** Expert level – full mastery. Highest grade in the ordering. */
  LEVEL_C;

  /**
   * Returns whether this level satisfies the given minimum requirement, i.e. whether {@code this >=
   * minimum} in the {@link PromotionLevel#LEVEL_A} &lt; {@link #LEVEL_B} &lt; {@link #LEVEL_C}
   * ordering. Used by the eligibility service when matching a {@code MemberEvaluation} against a
   * {@code RankRequirement}.
   *
   * @param minimum the minimum grade the requirement demands; never {@code null}
   * @return {@code true} iff this level is at least as high as {@code minimum}
   */
  public boolean isAtLeast(@NotNull PromotionLevel minimum) {
    return this.ordinal() >= minimum.ordinal();
  }
}
