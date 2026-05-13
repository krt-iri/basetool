package de.greluc.krt.iri.basetool.backend;

import de.greluc.krt.iri.basetool.backend.controller.MaterialController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MaterialControllerTempTest {

  @Autowired private MaterialController controller;

  @Test
  public void test() {
    controller.getMaterialPrices(UUID.randomUUID(), 0, 1000, null);
  }
}
