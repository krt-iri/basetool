package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.InventoryItemMapper;
import de.greluc.krt.iri.basetool.backend.mapper.JobOrderMapper;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial;
import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderReferenceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UserReferenceDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for {@link JobOrderService} methods that the main
 * {@code JobOrderServiceTest} doesn't reach: the list/page/reference
 * getters and the {@code addAssignee} / {@code removeAssignee} pair.
 * Previously all of these methods were at 0% coverage according to JaCoCo.
 *
 * <p>Lives as a sibling test so the existing 586-line test file doesn't
 * need to gain a {@code UserRepository} mock (which would dirty its
 * dependency surface for tests that don't need it).
 */
@ExtendWith(MockitoExtension.class)
class JobOrderServiceAssigneeAndListTest {

    @Mock private JobOrderRepository jobOrderRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private JobOrderMapper jobOrderMapper;
    @Mock private InventoryItemMapper inventoryItemMapper;

    @InjectMocks
    private JobOrderService service;

    private static final UUID JOB_ORDER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void stubMapperEchoingEmptyMaterials() {
        // The service routes nearly every return through mapToDtoWithStock(),
        // which calls jobOrderMapper.toDto(...) and then iterates the result's
        // materials. Return an empty materials list so we don't have to stub
        // the stock-aggregation repository call.
        lenient().when(jobOrderMapper.toDto(any(JobOrder.class))).thenAnswer(inv -> {
            JobOrder o = inv.getArgument(0);
            return new JobOrderDto(
                    o.getId(),
                    o.getDisplayId(),
                    o.getSquadron(),
                    o.getHandle(),
                    o.getPriority(),
                    o.getStatus(),
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    o.getVersion());
        });
    }

    // ---------------------------------------------------------------
    // getAllJobOrders — status-filter routing
    // ---------------------------------------------------------------

    @Nested
    class GetAllJobOrdersTests {

        private final PageRequest pageable = PageRequest.of(0, 10);

        @Test
        void nullStatusList_callsFindAll() {
            Page<JobOrder> page = new PageImpl<>(List.of(newJobOrder(JobOrderStatus.OPEN)));
            when(jobOrderRepository.findAll(pageable)).thenReturn(page);

            Page<JobOrderDto> result = service.getAllJobOrders(null, pageable);

            assertEquals(1, result.getTotalElements());
            verify(jobOrderRepository, never()).findByStatusIn(any(), any());
        }

        @Test
        void emptyStatusList_callsFindAll() {
            Page<JobOrder> page = new PageImpl<>(List.of(newJobOrder(JobOrderStatus.OPEN)));
            when(jobOrderRepository.findAll(pageable)).thenReturn(page);

            service.getAllJobOrders(List.of(), pageable);

            verify(jobOrderRepository).findAll(pageable);
            verify(jobOrderRepository, never()).findByStatusIn(any(), any());
        }

        @Test
        void populatedStatusList_routedToFindByStatusIn() {
            Page<JobOrder> page = new PageImpl<>(List.of(newJobOrder(JobOrderStatus.OPEN)));
            when(jobOrderRepository.findByStatusIn(List.of(JobOrderStatus.OPEN), pageable))
                    .thenReturn(page);

            service.getAllJobOrders(List.of(JobOrderStatus.OPEN), pageable);

            verify(jobOrderRepository).findByStatusIn(List.of(JobOrderStatus.OPEN), pageable);
            verify(jobOrderRepository, never()).findAll(pageable);
        }
    }

    // ---------------------------------------------------------------
    // findAllActiveReference — null-materials ternary
    // ---------------------------------------------------------------

    @Nested
    class FindAllActiveReferenceTests {

        @Test
        void emptyRepositoryResult_returnsEmptyList() {
            when(jobOrderRepository.findAllActiveWithMaterials()).thenReturn(List.of());

            assertTrue(service.findAllActiveReference().isEmpty());
        }

        @Test
        void orderWithNullMaterials_emitsEmptyMaterialsList() {
            JobOrder o = newJobOrder(JobOrderStatus.OPEN);
            o.setMaterials(null);
            when(jobOrderRepository.findAllActiveWithMaterials()).thenReturn(List.of(o));

            List<JobOrderReferenceDto> result = service.findAllActiveReference();

            assertEquals(1, result.size());
            assertTrue(result.get(0).materials().isEmpty(),
                    "null materials on the entity must surface as an empty list, NOT NPE");
        }

        @Test
        void orderWithMaterials_mapsThroughTheMapper() {
            JobOrder o = newJobOrder(JobOrderStatus.OPEN);
            JobOrderMaterial mat = new JobOrderMaterial();
            mat.setId(UUID.randomUUID());
            mat.setMaterial(new de.greluc.krt.iri.basetool.backend.model.Material());
            o.setMaterials(new HashSet<>(Set.of(mat)));
            when(jobOrderRepository.findAllActiveWithMaterials()).thenReturn(List.of(o));
            when(jobOrderMapper.toDto(mat)).thenReturn(
                    new de.greluc.krt.iri.basetool.backend.model.dto.JobOrderMaterialDto(
                            mat.getId(), null, 100, 1.0, 0.0, 0L));

            List<JobOrderReferenceDto> result = service.findAllActiveReference();

            assertEquals(1, result.size());
            assertEquals(1, result.get(0).materials().size());
        }
    }

    // ---------------------------------------------------------------
    // getJobOrderById
    // ---------------------------------------------------------------

    @Nested
    class GetJobOrderByIdTests {

        @Test
        void happyPath_returnsDto() {
            JobOrder order = newJobOrder(JobOrderStatus.OPEN);
            when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));

            JobOrderDto dto = service.getJobOrderById(JOB_ORDER_ID);

            assertEquals(JOB_ORDER_ID, dto.id());
        }

        @Test
        void notFound_throwsNotFoundException() {
            when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.empty());

            NotFoundException ex = assertThrows(NotFoundException.class,
                    () -> service.getJobOrderById(JOB_ORDER_ID));
            assertTrue(ex.getMessage().contains(JOB_ORDER_ID.toString()),
                    "the missing id must be part of the message for diagnostics");
        }
    }

    // ---------------------------------------------------------------
    // addAssignee
    // ---------------------------------------------------------------

    @Nested
    class AddAssigneeTests {

        @Test
        void happyPath_addsUserToAssigneesAndSaves() {
            JobOrder order = newJobOrder(JobOrderStatus.OPEN);
            User user = newUser(USER_ID);
            when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(jobOrderRepository.save(order)).thenReturn(order);

            service.addAssignee(JOB_ORDER_ID, USER_ID);

            assertTrue(order.getAssignees().contains(user),
                    "user must be added to the assignees set");
            verify(jobOrderRepository).save(order);
        }

        @Test
        void notFoundJobOrder_throwsBeforeUserLookup() {
            when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> service.addAssignee(JOB_ORDER_ID, USER_ID));
            verify(userRepository, never()).findById(any());
            verify(jobOrderRepository, never()).save(any());
        }

        @Test
        void notFoundUser_throwsNotFoundException() {
            JobOrder order = newJobOrder(JobOrderStatus.OPEN);
            when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> service.addAssignee(JOB_ORDER_ID, USER_ID));
            verify(jobOrderRepository, never()).save(any());
        }

        @Test
        void addingExistingAssignee_isIdempotent() {
            // The assignees collection is a Set, so adding the same user twice
            // must not increase its size.
            User user = newUser(USER_ID);
            JobOrder order = newJobOrder(JobOrderStatus.OPEN);
            order.getAssignees().add(user);

            when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(jobOrderRepository.save(order)).thenReturn(order);

            service.addAssignee(JOB_ORDER_ID, USER_ID);

            assertEquals(1, order.getAssignees().size(),
                    "re-adding the same user must not duplicate (Set semantics)");
        }
    }

    // ---------------------------------------------------------------
    // removeAssignee
    // ---------------------------------------------------------------

    @Nested
    class RemoveAssigneeTests {

        @Test
        void happyPath_removesAssigneeAndSaves() {
            User user = newUser(USER_ID);
            JobOrder order = newJobOrder(JobOrderStatus.OPEN);
            order.getAssignees().add(user);

            when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(jobOrderRepository.save(order)).thenReturn(order);

            service.removeAssignee(JOB_ORDER_ID, USER_ID);

            assertTrue(order.getAssignees().isEmpty());
            verify(jobOrderRepository).save(order);
        }

        @Test
        void removingNonAssignee_isANoOpButStillSaves() {
            // Set.remove(unknown) returns false but does not throw. The current
            // implementation does NOT specially handle this case — the save
            // happens regardless. Locking this in so a future "throw if not
            // already assigned" change requires conscious test updates.
            User user = newUser(USER_ID);
            JobOrder order = newJobOrder(JobOrderStatus.OPEN);
            // user is NOT in assignees

            when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(jobOrderRepository.save(order)).thenReturn(order);

            service.removeAssignee(JOB_ORDER_ID, USER_ID);

            assertTrue(order.getAssignees().isEmpty());
            verify(jobOrderRepository).save(order);
        }

        @Test
        void notFoundJobOrder_throws() {
            when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> service.removeAssignee(JOB_ORDER_ID, USER_ID));
            verify(userRepository, never()).findById(any());
        }

        @Test
        void notFoundUser_throws() {
            JobOrder order = newJobOrder(JobOrderStatus.OPEN);
            when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> service.removeAssignee(JOB_ORDER_ID, USER_ID));
            verify(jobOrderRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private JobOrder newJobOrder(JobOrderStatus status) {
        JobOrder o = new JobOrder();
        o.setId(JOB_ORDER_ID);
        o.setStatus(status);
        o.setVersion(1L);
        return o;
    }

    private User newUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setUsername("user-" + id);
        return u;
    }
}
