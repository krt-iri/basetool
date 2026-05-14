package de.greluc.krt.iri.basetool.backend.validation;

import java.util.UUID;

/**
 * Marker contract implemented by write-DTOs that carry a material reference and an amount and
 * therefore need to be validated by {@link ValidQuantityAmountValidator}.
 *
 * <p>Implementing this interface lets the validator pull both fields without reflection or
 * MapStruct introspection, and lets us put {@code @ValidQuantityAmount} on any DTO that exposes the
 * pair regardless of unrelated fields. The validator looks up the material's {@link
 * de.greluc.krt.iri.basetool.backend.model.QuantityType} and enforces either integer (PIECE) or
 * &le;3-decimal (SCU) precision on {@link #amount()}.
 */
public interface QuantityAware {
  /**
   * Returns UUID of the referenced {@link de.greluc.krt.iri.basetool.backend.model.Material}; may
   * be {@code null} during validation if {@code @NotNull} on the field hasn't fired yet.
   *
   * @return UUID of the referenced {@link de.greluc.krt.iri.basetool.backend.model.Material}; may
   *     be {@code null} during validation if {@code @NotNull} on the field hasn't fired yet
   */
  UUID materialId();

  /**
   * Returns requested quantity; may be {@code null} during validation if {@code @NotNull} hasn't
   * fired yet.
   *
   * @return requested quantity; may be {@code null} during validation if {@code @NotNull} hasn't
   *     fired yet
   */
  Double amount();
}
