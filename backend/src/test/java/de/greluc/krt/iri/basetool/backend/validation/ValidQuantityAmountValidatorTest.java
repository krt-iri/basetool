package de.greluc.krt.iri.basetool.backend.validation;

import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.QuantityType;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidQuantityAmountValidatorTest {

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder builder;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext nodeBuilder;

    private ValidQuantityAmountValidator validator;
    private UUID materialId;

    @BeforeEach
    void setUp() {
        validator = new ValidQuantityAmountValidator(materialRepository);
        materialId = UUID.randomUUID();
    }

    @Test
    void shouldBeValidWhenNull() {
        QuantityAware dto = new TestDto(null, null);
        assertTrue(validator.isValid(dto, context));
        
        dto = new TestDto(materialId, null);
        assertTrue(validator.isValid(dto, context));
        
        dto = new TestDto(null, 1.0);
        assertTrue(validator.isValid(dto, context));
    }

    @Test
    void shouldBeInvalidWhenNegativeOrZero() {
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode("amount")).thenReturn(nodeBuilder);

        QuantityAware dto0 = new TestDto(materialId, 0.0);
        assertFalse(validator.isValid(dto0, context));

        QuantityAware dtoNeg = new TestDto(materialId, -5.0);
        assertFalse(validator.isValid(dtoNeg, context));

        verify(context, times(2)).disableDefaultConstraintViolation();
        verify(context, times(2)).buildConstraintViolationWithTemplate("{error.validation.quantity_must_be_positive}");
    }

    @Test
    void shouldBeValidWhenPieceIsInteger() {
        Material material = new Material();
        material.setQuantityType(QuantityType.PIECE);
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

        QuantityAware dto = new TestDto(materialId, 5.0);
        assertTrue(validator.isValid(dto, context));
    }

    @Test
    void shouldBeInvalidWhenPieceIsDecimal() {
        Material material = new Material();
        material.setQuantityType(QuantityType.PIECE);
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode("amount")).thenReturn(nodeBuilder);

        QuantityAware dto = new TestDto(materialId, 5.5);
        assertFalse(validator.isValid(dto, context));

        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("{error.validation.quantity_must_be_integer}");
    }

    @Test
    void shouldBeValidWhenScuHasUpToThreeDecimals() {
        Material material = new Material();
        material.setQuantityType(QuantityType.SCU);
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

        assertTrue(validator.isValid(new TestDto(materialId, 10.0), context));
        assertTrue(validator.isValid(new TestDto(materialId, 10.12), context));
        assertTrue(validator.isValid(new TestDto(materialId, 10.123), context));
    }

    @Test
    void shouldBeInvalidWhenScuHasMoreThanThreeDecimals() {
        Material material = new Material();
        material.setQuantityType(QuantityType.SCU);
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode("amount")).thenReturn(nodeBuilder);

        assertFalse(validator.isValid(new TestDto(materialId, 10.1234), context));

        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("{error.validation.quantity_max_3_decimals}");
    }

    record TestDto(UUID materialId, Double amount) implements QuantityAware {}
}