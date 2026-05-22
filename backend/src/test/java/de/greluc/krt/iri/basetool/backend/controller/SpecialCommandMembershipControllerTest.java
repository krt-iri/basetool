package de.greluc.krt.iri.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.mapper.OrgUnitMembershipMapper;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.iri.basetool.backend.model.dto.MembershipFlagsPatchRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.MembershipLeadToggleRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.OrgUnitMembershipDto;
import de.greluc.krt.iri.basetool.backend.service.OrgUnitMembershipService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure-method unit tests for {@link SpecialCommandMembershipController}. The Spring-MVC binding
 * ({@code @PreAuthorize} SpEL on the security service, JSON marshalling) is covered by
 * integration tests; here we pin the controller's delegation to the service + mapper.
 */
@ExtendWith(MockitoExtension.class)
class SpecialCommandMembershipControllerTest {

  @Mock private OrgUnitMembershipService service;
  @Mock private OrgUnitMembershipMapper mapper;

  @InjectMocks private SpecialCommandMembershipController controller;

  private static OrgUnitMembershipDto sampleDto(UUID userId, UUID scId) {
    return new OrgUnitMembershipDto(
        userId,
        "Alice",
        scId,
        OrgUnitKind.SPECIAL_COMMAND,
        false,
        false,
        false,
        Instant.now(),
        0L);
  }

  @Test
  void listMembers_mapsEntityListThroughMapper() {
    UUID scId = UUID.randomUUID();
    OrgUnitMembership entity = new OrgUnitMembership();
    OrgUnitMembershipDto dto = sampleDto(UUID.randomUUID(), scId);
    when(service.listMembers(scId)).thenReturn(List.of(entity));
    when(mapper.toDto(entity)).thenReturn(dto);

    List<OrgUnitMembershipDto> result = controller.listMembers(scId);

    assertEquals(1, result.size());
    assertSame(dto, result.get(0));
  }

  @Test
  void addMember_delegatesAndMaps() {
    UUID scId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    OrgUnitMembership entity = new OrgUnitMembership();
    OrgUnitMembershipDto dto = sampleDto(userId, scId);
    when(service.addMember(scId, userId)).thenReturn(entity);
    when(mapper.toDto(entity)).thenReturn(dto);

    OrgUnitMembershipDto result = controller.addMember(scId, userId);

    assertSame(dto, result);
    verify(service).addMember(scId, userId);
  }

  @Test
  void removeMember_delegatesIdsToService() {
    UUID scId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    controller.removeMember(scId, userId);

    verify(service).removeMember(scId, userId);
    verifyNoMoreInteractions(service);
  }

  @Test
  void patchFlags_delegatesAndMaps() {
    UUID scId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    MembershipFlagsPatchRequest request = new MembershipFlagsPatchRequest(true, null, 4L);
    OrgUnitMembership entity = new OrgUnitMembership();
    OrgUnitMembershipDto dto = sampleDto(userId, scId);
    when(service.patchFlags(scId, userId, request)).thenReturn(entity);
    when(mapper.toDto(entity)).thenReturn(dto);

    OrgUnitMembershipDto result = controller.patchFlags(scId, userId, request);

    assertSame(dto, result);
    verify(service).patchFlags(eq(scId), eq(userId), any(MembershipFlagsPatchRequest.class));
  }

  @Test
  void toggleLead_delegatesAndMaps() {
    UUID scId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    MembershipLeadToggleRequest request = new MembershipLeadToggleRequest(true, 0L);
    OrgUnitMembership entity = new OrgUnitMembership();
    OrgUnitMembershipDto dto = sampleDto(userId, scId);
    when(service.toggleLead(scId, userId, request)).thenReturn(entity);
    when(mapper.toDto(entity)).thenReturn(dto);

    OrgUnitMembershipDto result = controller.toggleLead(scId, userId, request);

    assertSame(dto, result);
    verify(service).toggleLead(eq(scId), eq(userId), any(MembershipLeadToggleRequest.class));
  }
}
