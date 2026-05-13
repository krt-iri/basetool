package de.greluc.krt.iri.basetool.backend.validation;

import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.QuantityType;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Constraint validator for {@link ValidQuantityAmount}.
 *
 * <p>Resolves the material by id (one DB hit per validated DTO) and applies the
 * quantity-type-specific rules listed on {@link ValidQuantityAmount}. If the material does not
 * exist, validation silently passes — the surrounding {@code @NotNull}/foreign-key checks (or the
 * service layer) report that case so the user gets the right error key, not a confusing "invalid
 * quantity" message.
 */
@Component
@RequiredArgsConstructor
public class ValidQuantityAmountValidator
    implements ConstraintValidator<ValidQuantityAmount, QuantityAware> {

  private final MaterialRepository materialRepository;

  @Override
  public boolean isValid(QuantityAware dto, ConstraintValidatorContext context) {
    if (dto == null || dto.materialId() == null || dto.amount() == null) {
      return true; // Let @NotNull handle these
    }

    if (dto.amount() <= 0) {
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate("{error.validation.quantity_must_be_positive}")
          .addPropertyNode("amount")
          .addConstraintViolation();
      return false;
    }

    Optional<Material> materialOpt = materialRepository.findById(dto.materialId());
    if (materialOpt.isEmpty()) {
      return true; // Let other validations handle missing material
    }

    // The isEmpty()/early-return guard above already excludes the empty case;
    // orElseThrow makes that contract explicit (and silences SpotBugs).
    Material material = materialOpt.orElseThrow();
    if (material.getQuantityType() == QuantityType.PIECE) {
      if (dto.amount() % 1 != 0) {
        context.disableDefaultConstraintViolation();
        context
            .buildConstraintViolationWithTemplate("{error.validation.quantity_must_be_integer}")
            .addPropertyNode("amount")
            .addConstraintViolation();
        return false;
      }
    } else if (material.getQuantityType() == QuantityType.SCU) {
      BigDecimal bd = BigDecimal.valueOf(dto.amount()).stripTrailingZeros();
      if (bd.scale() > 3) {
        context.disableDefaultConstraintViolation();
        context
            .buildConstraintViolationWithTemplate("{error.validation.quantity_max_3_decimals}")
            .addPropertyNode("amount")
            .addConstraintViolation();
        return false;
      }
    }

    return true;
  }
}
