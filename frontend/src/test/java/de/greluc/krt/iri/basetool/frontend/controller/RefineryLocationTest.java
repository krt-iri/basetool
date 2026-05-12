package de.greluc.krt.iri.basetool.frontend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import de.greluc.krt.iri.basetool.frontend.model.dto.LocationDto;

import java.util.List;

public class RefineryLocationTest {

    @Test
    public void testTypeReference() {
        ParameterizedTypeReference<List<LocationDto>> typeRef = new ParameterizedTypeReference<>() {};
        typeRef.getType();
    }
}
