package de.greluc.krt.iri.basetool.backend;

import de.greluc.krt.iri.basetool.backend.repository.MaterialPriceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.UUID;

@SpringBootTest
public class MaterialPriceTempTest {

    @Autowired
    private MaterialPriceRepository repository;

    @Test
    public void testFindPrices() {
        try {
            repository.findPricesByMaterialId(UUID.randomUUID(), PageRequest.of(0, 1000, Sort.by("terminal.name").and(Sort.by("id"))));
            System.out.println("TEST SUCCESS: No exception thrown");
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
