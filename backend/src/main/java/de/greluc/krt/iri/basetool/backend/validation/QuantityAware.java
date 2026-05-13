package de.greluc.krt.iri.basetool.backend.validation;

import java.util.UUID;

public interface QuantityAware {
  UUID materialId();

  Double amount();
}
