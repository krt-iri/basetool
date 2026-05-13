package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.LocationDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

public class RefineryLocationTest {

  @Test
  public void testTypeReference() {
    ParameterizedTypeReference<List<LocationDto>> typeRef = new ParameterizedTypeReference<>() {};
    typeRef.getType();
  }
}
