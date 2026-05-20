package de.greluc.krt.iri.basetool.frontend.view;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Spring bean exposed to Thymeleaf templates that rounds monetary amounts to whole aUEC using
 * {@link RoundingMode#HALF_UP} ("kaufmaennische Rundung" — 0.5 always rounds up, away from zero).
 *
 * <p>Why this exists: Thymeleaf's {@code #numbers.formatInteger} delegates to {@link
 * java.text.DecimalFormat}, whose default rounding mode is {@link RoundingMode#HALF_EVEN} (banker's
 * rounding). For aUEC payout, finance, and refinery totals the user-visible expectation is
 * commercial rounding, where 0.5 always rounds up — banker's rounding would surface as values
 * ending in .5 being inconsistently rounded depending on the integer part's parity. This bean
 * pre-rounds the value once, server-side; the template then passes the already-integer-scaled
 * {@link BigDecimal} into {@code #numbers.formatInteger} where the rounding mode no longer matters.
 *
 * <p>Spring's {@code @beanName.method(arg)} SpEL syntax is the access path from Thymeleaf — direct
 * static-method or {@code T(java.math.RoundingMode)} access is disabled by Thymeleaf 3.1+ for
 * security reasons, so the bean is the canonical way to thread {@link RoundingMode#HALF_UP} into a
 * template expression. Usage:
 *
 * <pre>{@code
 * <span th:text="${#numbers.formatInteger(@moneyFormat.round(amount), 1, 'POINT')}
 *                + ' aUEC'"></span>
 * }</pre>
 */
@Component("moneyFormat")
public class MoneyFormat {

  /**
   * Rounds the given {@link BigDecimal} to scale 0 using {@link RoundingMode#HALF_UP}.
   *
   * @param value the amount to round; {@code null} is passed through so the calling template
   *     renders it the same way it would have without the bean
   * @return the value rounded to a whole number, or {@code null} if {@code value} was {@code null}
   */
  @Nullable
  public BigDecimal round(@Nullable BigDecimal value) {
    if (value == null) {
      return null;
    }
    return value.setScale(0, RoundingMode.HALF_UP);
  }

  /**
   * Variant of {@link #round(BigDecimal)} for {@link Number} arguments (refinery-order expenses
   * arrive as {@link Double} on the frontend DTOs). Deliberately named distinctly from {@link
   * #round(BigDecimal)} so the Thymeleaf SpEL dispatch is unambiguous at template-author read time
   * — the two methods are not overloads. The value is converted via its canonical {@link
   * Number#toString()} representation to avoid the {@code new BigDecimal(double)} precision
   * artefact (e.g. 0.1 becoming 0.1000000000000000055511...), then delegated to {@link
   * #round(BigDecimal)}.
   *
   * @param value the amount to round; {@code null} is passed through so the calling template
   *     renders it the same way it would have without the bean
   * @return the value rounded to a whole number, or {@code null} if {@code value} was {@code null}
   */
  @Nullable
  public BigDecimal roundNumber(@Nullable Number value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal bd) {
      return round(bd);
    }
    return round(new BigDecimal(value.toString()));
  }
}
