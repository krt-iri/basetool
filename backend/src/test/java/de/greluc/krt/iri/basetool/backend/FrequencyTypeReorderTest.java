package de.greluc.krt.iri.basetool.backend;

import de.greluc.krt.iri.basetool.backend.model.FrequencyType;
import de.greluc.krt.iri.basetool.backend.repository.FrequencyTypeRepository;
import de.greluc.krt.iri.basetool.backend.service.FrequencyTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class FrequencyTypeReorderTest {

    @Autowired
    private FrequencyTypeService frequencyTypeService;

    @Autowired
    private FrequencyTypeRepository frequencyTypeRepository;

    @Test
    @Transactional
    void testReorder() {
        FrequencyType ft1 = new FrequencyType();
        ft1.setName("Type A");
        ft1.setSortIndex(0);
        ft1 = frequencyTypeRepository.saveAndFlush(ft1);

        FrequencyType ft2 = new FrequencyType();
        ft2.setName("Type B");
        ft2.setSortIndex(0);
        ft2 = frequencyTypeRepository.saveAndFlush(ft2);

        frequencyTypeService.reorderFrequencyTypes(List.of(ft2.getId(), ft1.getId()));
        
        frequencyTypeRepository.flush();

        FrequencyType updatedFt1 = frequencyTypeRepository.findById(ft1.getId()).orElseThrow();
        FrequencyType updatedFt2 = frequencyTypeRepository.findById(ft2.getId()).orElseThrow();

        assertEquals(1, updatedFt1.getSortIndex());
        assertEquals(0, updatedFt2.getSortIndex());
    }
}