package de.greluc.krt.iri.basetool.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidQuantityAmountValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidQuantityAmount {
    String message() default "{error.validation.quantity_amount_invalid}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
