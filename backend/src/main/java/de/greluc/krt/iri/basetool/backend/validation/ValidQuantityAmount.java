package de.greluc.krt.iri.basetool.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ValidQuantityAmountValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidQuantityAmount {
  String message() default "{error.validation.quantity_amount_invalid}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
