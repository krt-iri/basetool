package de.greluc.krt.iri.basetool.backend.model.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.greluc.krt.iri.basetool.backend.model.PersonalInventoryLocationType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Bean Validation contract tests for the personal inventory write DTOs. Failures here would
 * silently allow malformed payloads through the {@code @Valid}-annotated controller methods – treat
 * any new failure as a regression.
 */
class PersonalInventoryItemRequestValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void initValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeFactory() {
    if (factory != null) {
      factory.close();
    }
  }

  @Test
  void validCreateRequestShouldHaveNoViolations() {
    // Given
    PersonalInventoryItemCreateRequest req =
        new PersonalInventoryItemCreateRequest(
            "Medkit", "first aid", 42, PersonalInventoryLocationType.CITY, 3);

    // When / Then
    assertTrue(validator.validate(req).isEmpty());
  }

  @Test
  void blankNameShouldBeRejected() {
    PersonalInventoryItemCreateRequest req =
        new PersonalInventoryItemCreateRequest(
            "  ", null, 1, PersonalInventoryLocationType.CITY, 1);

    Set<ConstraintViolation<PersonalInventoryItemCreateRequest>> violations =
        validator.validate(req);

    assertFalse(violations.isEmpty());
    assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")));
  }

  @Test
  void overlongNameShouldBeRejected() {
    String tooLong = "x".repeat(121);
    PersonalInventoryItemCreateRequest req =
        new PersonalInventoryItemCreateRequest(
            tooLong, null, 1, PersonalInventoryLocationType.CITY, 1);

    assertFalse(validator.validate(req).isEmpty());
  }

  @Test
  void overlongNoteShouldBeRejected() {
    String tooLong = "x".repeat(2001);
    PersonalInventoryItemCreateRequest req =
        new PersonalInventoryItemCreateRequest(
            "ok", tooLong, 1, PersonalInventoryLocationType.CITY, 1);

    assertFalse(validator.validate(req).isEmpty());
  }

  @Test
  void quantityBelowOneShouldBeRejected() {
    PersonalInventoryItemCreateRequest req =
        new PersonalInventoryItemCreateRequest(
            "ok", null, 1, PersonalInventoryLocationType.CITY, 0);

    Set<ConstraintViolation<PersonalInventoryItemCreateRequest>> violations =
        validator.validate(req);

    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("quantity")));
  }

  @Test
  void missingLocationFieldsShouldBeRejected() {
    PersonalInventoryItemCreateRequest req =
        new PersonalInventoryItemCreateRequest("ok", null, null, null, 1);

    Set<ConstraintViolation<PersonalInventoryItemCreateRequest>> violations =
        validator.validate(req);

    assertEquals(2, violations.size());
  }

  @Test
  void updateRequestRequiresVersion() {
    PersonalInventoryItemUpdateRequest req =
        new PersonalInventoryItemUpdateRequest(
            "ok", null, 1, PersonalInventoryLocationType.CITY, 1, null);

    Set<ConstraintViolation<PersonalInventoryItemUpdateRequest>> violations =
        validator.validate(req);

    assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("version")));
  }

  @Test
  void validUpdateRequestShouldPass() {
    PersonalInventoryItemUpdateRequest req =
        new PersonalInventoryItemUpdateRequest(
            "ok", null, 1, PersonalInventoryLocationType.CITY, 1, 0L);

    assertTrue(validator.validate(req).isEmpty());
  }
}
