/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.profit.basetool.backend.exception.NotFoundException;
import de.greluc.krt.profit.basetool.backend.mapper.InventoryItemMapper;
import de.greluc.krt.profit.basetool.backend.mapper.JobOrderMapper;
import de.greluc.krt.profit.basetool.backend.model.JobOrder;
import de.greluc.krt.profit.basetool.backend.model.JobOrderAssignee;
import de.greluc.krt.profit.basetool.backend.model.JobOrderMaterial;
import de.greluc.krt.profit.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.profit.basetool.backend.model.JobOrderType;
import de.greluc.krt.profit.basetool.backend.model.User;
import de.greluc.krt.profit.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.profit.basetool.backend.model.dto.JobOrderReferenceDto;
import de.greluc.krt.profit.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.profit.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.profit.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.profit.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.profit.basetool.backend.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Coverage for {@link JobOrderService} methods that the main {@code JobOrderServiceTest} doesn't
 * reach: the list/page/reference getters and the {@code addAssignee} / {@code removeAssignee} pair.
 * Previously all of these methods were at 0% coverage according to JaCoCo.
 *
 * <p>Lives as a sibling test so the existing 586-line test file doesn't need to gain a {@code
 * UserRepository} mock (which would dirty its dependency surface for tests that don't need it).
 */
@ExtendWith(MockitoExtension.class)
class JobOrderServiceAssigneeAndListTest {

  @Mock private JobOrderRepository jobOrderRepository;
  @Mock private MaterialRepository materialRepository;
  @Mock private InventoryItemRepository inventoryItemRepository;
  @Mock private UserRepository userRepository;
  @Mock private SquadronRepository squadronRepository;
  @Mock private OwnerScopeService ownerScopeService;
  @Mock private JobOrderMapper jobOrderMapper;
  @Mock private InventoryItemMapper inventoryItemMapper;

  @InjectMocks private JobOrderService service;

  private static final UUID JOB_ORDER_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  @BeforeEach
  void stubMapperEchoingEmptyMaterials() {
    // The service routes nearly every return through mapToDtoWithStock(),
    // which calls jobOrderMapper.toDto(...) and then iterates the result's
    // materials. Return an empty materials list so we don't have to stub
    // the stock-aggregation repository call.
    lenient()
        .when(jobOrderMapper.toDto(any(JobOrder.class)))
        .thenAnswer(
            inv -> {
              JobOrder o = inv.getArgument(0);
              return new JobOrderDto(
                  o.getId(),
                  o.getDisplayId(),
                  null,
                  null,
                  o.getHandle(),
                  o.getComment(),
                  o.getPriority(),
                  o.getStatus(),
                  JobOrderType.MATERIAL,
                  List.of(),
                  List.of(),
                  List.of(),
                  List.of(),
                  List.of(),
                  List.of(),
                  null,
                  o.getVersion());
            });
  }

  // ---------------------------------------------------------------
  // getAllJobOrders — status-filter routing + visibility scope (Phase 3, #343)
  // ---------------------------------------------------------------

  @Nested
  class GetAllJobOrdersTests {

    private final PageRequest pageable = PageRequest.of(0, 10);
    // The scope predicate is resolved from OwnerScopeService and pushed into the repository query;
    // every list call must consult it. An admin-all-scope predicate keeps these routing tests
    // focused on the status/squadron-filter forwarding rather than the scope semantics (those live
    // in OwnerScopeServiceTest).
    private final ScopePredicate adminAllScope = new ScopePredicate(true, null, Set.of());

    // No status filter → the service passes the full enum set so the repository IN clause is never
    // bound with an empty collection.
    private final List<JobOrderStatus> allStatuses = List.of(JobOrderStatus.values());

    @BeforeEach
    void stubScope() {
      lenient().when(ownerScopeService.currentScopePredicate()).thenReturn(adminAllScope);
      // These routing tests model a permitted viewer; the profit gate is covered separately in
      // OwnerScopeServiceTest / JobOrderServiceTest, so let the list reach the repository here.
      lenient().when(ownerScopeService.canViewJobOrders()).thenReturn(true);
    }

    @Test
    void nullStatusList_passesFullEnumSet() {
      Page<JobOrder> page = new PageImpl<>(List.of(newJobOrder(JobOrderStatus.OPEN)));
      when(jobOrderRepository.findScopedJobOrders(
              allStatuses, null, true, null, Set.of(), pageable))
          .thenReturn(page);

      Page<JobOrderDto> result = service.getAllJobOrders(null, pageable);

      assertEquals(1, result.getTotalElements());
      verify(jobOrderRepository)
          .findScopedJobOrders(allStatuses, null, true, null, Set.of(), pageable);
    }

    @Test
    void emptyStatusList_passesFullEnumSet() {
      Page<JobOrder> page = new PageImpl<>(List.of(newJobOrder(JobOrderStatus.OPEN)));
      when(jobOrderRepository.findScopedJobOrders(
              allStatuses, null, true, null, Set.of(), pageable))
          .thenReturn(page);

      service.getAllJobOrders(List.of(), pageable);

      verify(jobOrderRepository)
          .findScopedJobOrders(allStatuses, null, true, null, Set.of(), pageable);
    }

    @Test
    void populatedStatusList_forwardsStatusesAndScope() {
      Page<JobOrder> page = new PageImpl<>(List.of(newJobOrder(JobOrderStatus.OPEN)));
      when(jobOrderRepository.findScopedJobOrders(
              List.of(JobOrderStatus.OPEN), null, true, null, Set.of(), pageable))
          .thenReturn(page);

      service.getAllJobOrders(List.of(JobOrderStatus.OPEN), pageable);

      verify(jobOrderRepository)
          .findScopedJobOrders(List.of(JobOrderStatus.OPEN), null, true, null, Set.of(), pageable);
    }

    @Test
    void populatedStatusListWithSquadronId_passesSquadronDisplayFilter() {
      Page<JobOrder> page = new PageImpl<>(List.of(newJobOrder(JobOrderStatus.OPEN)));
      UUID squadronId = UUID.randomUUID();
      when(jobOrderRepository.findScopedJobOrders(
              List.of(JobOrderStatus.OPEN), squadronId, true, null, Set.of(), pageable))
          .thenReturn(page);

      service.getAllJobOrders(List.of(JobOrderStatus.OPEN), squadronId, pageable);

      verify(jobOrderRepository)
          .findScopedJobOrders(
              List.of(JobOrderStatus.OPEN), squadronId, true, null, Set.of(), pageable);
    }

    @Test
    void emptyStatusListWithSquadronId_keepsDisplayFilterAndFullEnumSet() {
      Page<JobOrder> page = new PageImpl<>(List.of(newJobOrder(JobOrderStatus.OPEN)));
      UUID squadronId = UUID.randomUUID();
      when(jobOrderRepository.findScopedJobOrders(
              allStatuses, squadronId, true, null, Set.of(), pageable))
          .thenReturn(page);

      service.getAllJobOrders(List.of(), squadronId, pageable);

      verify(jobOrderRepository)
          .findScopedJobOrders(allStatuses, squadronId, true, null, Set.of(), pageable);
    }

    @Test
    void nonAdminScope_forwardsMemberUnionToRepository() {
      // A non-admin member with a two-OrgUnit membership union: the predicate's memberOrgUnitIds
      // must reach the repository verbatim so the IN-clause scoping applies.
      Page<JobOrder> page = new PageImpl<>(List.of(newJobOrder(JobOrderStatus.OPEN)));
      UUID sqA = UUID.randomUUID();
      UUID sqB = UUID.randomUUID();
      Set<UUID> union = Set.of(sqA, sqB);
      when(ownerScopeService.currentScopePredicate())
          .thenReturn(new ScopePredicate(false, null, union));
      when(jobOrderRepository.findScopedJobOrders(allStatuses, null, false, null, union, pageable))
          .thenReturn(page);

      service.getAllJobOrders(null, pageable);

      verify(jobOrderRepository)
          .findScopedJobOrders(allStatuses, null, false, null, union, pageable);
    }
  }

  // ---------------------------------------------------------------
  // findAllActiveReference — null-materials ternary
  // ---------------------------------------------------------------

  @Nested
  class FindAllActiveReferenceTests {

    @Test
    void emptyRepositoryResult_returnsEmptyList() {
      when(ownerScopeService.canViewJobOrders()).thenReturn(true);
      when(jobOrderRepository.findAllActiveWithMaterials()).thenReturn(List.of());

      assertTrue(service.findAllActiveReference().isEmpty());
    }

    @Test
    void nonProfitMember_getsEmptyListWithoutQueryingRepository() {
      // M-2: the viewer-side profit gate short-circuits before the repository is even touched.
      when(ownerScopeService.canViewJobOrders()).thenReturn(false);

      assertTrue(service.findAllActiveReference().isEmpty());
      verify(jobOrderRepository, never()).findAllActiveWithMaterials();
    }

    @Test
    void ordersOutOfScope_areFilteredOut() {
      // M-2: a squadron-private order the caller may not see is dropped from the typeahead so it
      // cannot enumerate a foreign squadron's order handle + materials.
      JobOrder o = newJobOrder(JobOrderStatus.OPEN);
      when(ownerScopeService.canViewJobOrders()).thenReturn(true);
      when(ownerScopeService.canSeeJobOrder(any(JobOrder.class))).thenReturn(false);
      when(jobOrderRepository.findAllActiveWithMaterials()).thenReturn(List.of(o));

      assertTrue(service.findAllActiveReference().isEmpty());
    }

    @Test
    void orderWithNullMaterials_emitsEmptyMaterialsList() {
      JobOrder o = newJobOrder(JobOrderStatus.OPEN);
      o.setMaterials(null);
      when(ownerScopeService.canViewJobOrders()).thenReturn(true);
      when(ownerScopeService.canSeeJobOrder(any(JobOrder.class))).thenReturn(true);
      when(jobOrderRepository.findAllActiveWithMaterials()).thenReturn(List.of(o));

      List<JobOrderReferenceDto> result = service.findAllActiveReference();

      assertEquals(1, result.size());
      assertTrue(
          result.get(0).materials().isEmpty(),
          "null materials on the entity must surface as an empty list, NOT NPE");
    }

    @Test
    void orderWithMaterials_mapsThroughTheMapper() {
      JobOrder o = newJobOrder(JobOrderStatus.OPEN);
      JobOrderMaterial mat = new JobOrderMaterial();
      mat.setId(UUID.randomUUID());
      mat.setMaterial(new de.greluc.krt.profit.basetool.backend.model.Material());
      o.setMaterials(new HashSet<>(Set.of(mat)));
      when(ownerScopeService.canViewJobOrders()).thenReturn(true);
      when(ownerScopeService.canSeeJobOrder(any(JobOrder.class))).thenReturn(true);
      when(jobOrderRepository.findAllActiveWithMaterials()).thenReturn(List.of(o));
      when(jobOrderMapper.toDto(mat))
          .thenReturn(
              new de.greluc.krt.profit.basetool.backend.model.dto.JobOrderMaterialDto(
                  mat.getId(), null, 100, 1.0, 0.0, List.of(), null, 0L));

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

      NotFoundException ex =
          assertThrows(NotFoundException.class, () -> service.getJobOrderById(JOB_ORDER_ID));
      assertTrue(
          ex.getMessage().contains(JOB_ORDER_ID.toString()),
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
      when(jobOrderRepository.saveAndFlush(order)).thenReturn(order);

      service.addAssignee(JOB_ORDER_ID, USER_ID);

      assertTrue(
          order.getAssignees().stream().anyMatch(a -> USER_ID.equals(a.getUser().getId())),
          "user must be added as an assignee edge");
      verify(jobOrderRepository).saveAndFlush(order);
    }

    @Test
    void notFoundJobOrder_throwsBeforeUserLookup() {
      when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.addAssignee(JOB_ORDER_ID, USER_ID));
      verify(userRepository, never()).findById(any());
      verify(jobOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void notFoundUser_throwsNotFoundException() {
      JobOrder order = newJobOrder(JobOrderStatus.OPEN);
      when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));
      when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.addAssignee(JOB_ORDER_ID, USER_ID));
      verify(jobOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void addingExistingAssignee_isIdempotent() {
      // A user is an assignee at most once per order; re-adding the same user is a no-op that
      // skips both the user lookup and the save.
      User user = newUser(USER_ID);
      JobOrder order = newJobOrder(JobOrderStatus.OPEN);
      assigneeEdge(order, user);

      when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));

      service.addAssignee(JOB_ORDER_ID, USER_ID);

      assertEquals(
          1, order.getAssignees().size(), "re-adding the same user must not duplicate the edge");
      verify(userRepository, never()).findById(any());
      verify(jobOrderRepository, never()).saveAndFlush(any());
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
      assigneeEdge(order, user);

      when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));
      when(jobOrderRepository.saveAndFlush(order)).thenReturn(order);

      service.removeAssignee(JOB_ORDER_ID, USER_ID);

      assertTrue(order.getAssignees().isEmpty());
      verify(jobOrderRepository).saveAndFlush(order);
    }

    @Test
    void removingNonAssignee_isANoOpButStillSaves() {
      // removeIf finds nothing but does not throw — the save happens regardless. The remove path
      // no longer looks the user up, so an unknown id is simply a no-op.
      JobOrder order = newJobOrder(JobOrderStatus.OPEN);
      // no assignee for USER_ID

      when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));
      when(jobOrderRepository.saveAndFlush(order)).thenReturn(order);

      service.removeAssignee(JOB_ORDER_ID, USER_ID);

      assertTrue(order.getAssignees().isEmpty());
      verify(jobOrderRepository).saveAndFlush(order);
    }

    @Test
    void notFoundJobOrder_throws() {
      when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.removeAssignee(JOB_ORDER_ID, USER_ID));
      verify(jobOrderRepository, never()).saveAndFlush(any());
    }
  }

  // ---------------------------------------------------------------
  // updateAssigneeNote / deleteAssigneeNote (REQ-ORDERS-013)
  // ---------------------------------------------------------------

  @Nested
  class AssigneeNoteTests {

    @Test
    void setNote_trimsAndFlushes() {
      JobOrder order = newJobOrder(JobOrderStatus.OPEN);
      JobOrderAssignee edge = assigneeEdge(order, newUser(USER_ID));
      edge.setVersion(3L);

      when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));
      when(jobOrderRepository.saveAndFlush(order)).thenReturn(order);

      service.updateAssigneeNote(JOB_ORDER_ID, USER_ID, "  works Friday  ", 3L);

      assertEquals("works Friday", edge.getNote(), "note is stored stripped");
      verify(jobOrderRepository).saveAndFlush(order);
    }

    @Test
    void setBlankNote_clearsIt() {
      JobOrder order = newJobOrder(JobOrderStatus.OPEN);
      JobOrderAssignee edge = assigneeEdge(order, newUser(USER_ID));
      edge.setNote("old");

      when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));
      when(jobOrderRepository.saveAndFlush(order)).thenReturn(order);

      service.updateAssigneeNote(JOB_ORDER_ID, USER_ID, "   ", null);

      assertNull(edge.getNote(), "a blank note clears the value");
    }

    @Test
    void deleteNote_clearsNote() {
      JobOrder order = newJobOrder(JobOrderStatus.OPEN);
      JobOrderAssignee edge = assigneeEdge(order, newUser(USER_ID));
      edge.setNote("old");
      edge.setVersion(5L);

      when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));
      when(jobOrderRepository.saveAndFlush(order)).thenReturn(order);

      service.deleteAssigneeNote(JOB_ORDER_ID, USER_ID, 5L);

      assertNull(edge.getNote());
    }

    @Test
    void staleVersion_throwsOptimisticLock() {
      JobOrder order = newJobOrder(JobOrderStatus.OPEN);
      JobOrderAssignee edge = assigneeEdge(order, newUser(USER_ID));
      edge.setVersion(7L);

      when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));

      assertThrows(
          ObjectOptimisticLockingFailureException.class,
          () -> service.updateAssigneeNote(JOB_ORDER_ID, USER_ID, "x", 3L));
      verify(jobOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    void unknownAssignee_throwsNotFound() {
      JobOrder order = newJobOrder(JobOrderStatus.OPEN);
      when(jobOrderRepository.findById(JOB_ORDER_ID)).thenReturn(Optional.of(order));

      assertThrows(
          NotFoundException.class,
          () -> service.updateAssigneeNote(JOB_ORDER_ID, USER_ID, "x", null));
      verify(jobOrderRepository, never()).saveAndFlush(any());
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

  private JobOrderAssignee assigneeEdge(JobOrder order, User user) {
    JobOrderAssignee edge = new JobOrderAssignee();
    edge.setId(UUID.randomUUID());
    edge.setUser(user);
    edge.setVersion(0L);
    order.addAssignee(edge);
    return edge;
  }
}
