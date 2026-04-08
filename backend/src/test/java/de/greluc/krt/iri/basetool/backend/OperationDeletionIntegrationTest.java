package de.greluc.krt.iri.basetool.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.backend.model.*;
import de.greluc.krt.iri.basetool.backend.repository.*;
import de.greluc.krt.iri.basetool.backend.service.OperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OperationDeletionIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private OperationRepository operationRepository;

    @Autowired
    private OperationService operationService;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private RefineryOrderRepository refineryOrderRepository;

    @Autowired
    private MissionFinanceEntryRepository missionFinanceEntryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private LocationRepository locationRepository;
    
    @Autowired
    private MissionParticipantRepository missionParticipantRepository;

    private User adminUser;
    private Operation operation;
    private Mission mission;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setUsername("admin");
        userRepository.save(adminUser);

        operation = new Operation();
        operation.setName("Deletion Test Op");
        operation.setStatus(OperationStatus.ACTIVE);
        operation = operationRepository.save(operation);

        mission = new Mission();
        mission.setName("Linked Mission");
        mission.setOperation(operation);
        mission.setStatus("PLANNED");
        mission = missionRepository.save(mission);
        
        operation.getMissions().add(mission);
        operationRepository.save(operation);
    }

    @Test
    void testDeleteSimpleOperation() throws Exception {
        Operation op = new Operation();
        op.setName("Simple Op");
        op.setStatus(OperationStatus.PLANNED);
        op = operationRepository.save(op);

        mockMvc.perform(delete("/api/v1/operations/" + op.getId())
                .with(jwt().jwt(builder -> builder.subject(adminUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());

        assertTrue(operationRepository.findById(op.getId()).isEmpty());
    }

    @Test
    void testDeleteOperationWithReferences() throws Exception {
        // Given: InventoryItem linked to Mission
        Material material = new Material();
        material.setName("Test Ore");
        material.setType(MaterialType.RAW);
        material = materialRepository.save(material);

        Location location = new Location();
        location.setName("Test Moon");
        location = locationRepository.save(location);

        InventoryItem item = new InventoryItem();
        item.setUser(adminUser);
        item.setMaterial(material);
        item.setLocation(location);
        item.setQuality(100);
        item.setAmount(10.0);
        item.setMission(mission);
        item = inventoryItemRepository.save(item);

        // Given: RefineryOrder linked to Mission
        RefineryOrder order = new RefineryOrder();
        order.setOwner(adminUser);
        order.setLocation(location);
        order.setMission(mission);
        order.setDurationMinutes(60L);
        order.setExpenses(100.0);
        order = refineryOrderRepository.save(order);

        // Given: MissionFinanceEntry linked to Mission
        MissionParticipant participant = new MissionParticipant();
        participant.setMission(mission);
        participant.setUser(adminUser);
        participant = missionParticipantRepository.save(participant);
        
        MissionFinanceEntry financeEntry = new MissionFinanceEntry();
        financeEntry.setMission(mission);
        financeEntry.setParticipant(participant);
        financeEntry.setType(FinanceType.EXPENSE);
        financeEntry.setAmount(new BigDecimal("50.00"));
        financeEntry = missionFinanceEntryRepository.save(financeEntry);

        // When: Deleting the operation as admin
        mockMvc.perform(delete("/api/v1/operations/" + operation.getId())
                .with(jwt().jwt(builder -> builder.subject(adminUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());

        // Then: Operation and Mission are deleted
        assertTrue(operationRepository.findById(operation.getId()).isEmpty());
        assertTrue(missionRepository.findById(mission.getId()).isEmpty());

        // Then: InventoryItem still exists but mission is unlinked
        InventoryItem updatedItem = inventoryItemRepository.findById(item.getId()).orElseThrow();
        assertNull(updatedItem.getMission());

        // Then: RefineryOrder still exists but mission is unlinked
        RefineryOrder updatedOrder = refineryOrderRepository.findById(order.getId()).orElseThrow();
        assertNull(updatedOrder.getMission());

        // Then: MissionFinanceEntry is deleted
        assertTrue(missionFinanceEntryRepository.findById(financeEntry.getId()).isEmpty());
    }
}
