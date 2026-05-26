package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembershipId;
import de.greluc.krt.iri.basetool.backend.model.SpecialCommand;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.MembershipFlagsPatchRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.MembershipLeadToggleRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.OrgUnitMembershipOptionDto;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.SpecialCommandRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Mockito unit tests for {@link OrgUnitMembershipService}. Pins the CRUD contract that the SK
 * member-management UI relies on: listing through the SK existence guard, add/remove happy paths
 * plus the duplicate-409 and not-found-404 paths, the flag-patch semantics including
 * optimistic-lock failures, and the dedicated lead toggle.
 */
@ExtendWith(MockitoExtension.class)
class OrgUnitMembershipServiceTest {

  @Mock private OrgUnitMembershipRepository membershipRepository;
  @Mock private SpecialCommandService specialCommandService;
  @Mock private UserRepository userRepository;
  @Mock private SquadronRepository squadronRepository;
  @Mock private SpecialCommandRepository specialCommandRepository;

  @InjectMocks private OrgUnitMembershipService membershipService;

  private SpecialCommand sc;
  private UUID scId;
  private User user;
  private UUID userId;
  private OrgUnitMembershipId id;

  @BeforeEach
  void setUp() {
    scId = UUID.randomUUID();
    sc = new SpecialCommand();
    sc.setId(scId);
    sc.setName("Alpha");
    sc.setShorthand("ALF");

    userId = UUID.randomUUID();
    user = new User();
    user.setId(userId);
    user.setUsername("alice");
    user.setDisplayName("Alice");

    id = new OrgUnitMembershipId(userId, scId);
  }

  // --- listMembers ----------------------------------------------------------

  @Test
  void listMembers_existingSc_returnsMembers() {
    OrgUnitMembership m = new OrgUnitMembership();
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findAllByIdOrgUnitId(scId)).thenReturn(List.of(m));

    List<OrgUnitMembership> result = membershipService.listMembers(scId);

    assertEquals(1, result.size());
    assertSame(m, result.get(0));
  }

  @Test
  void listMembers_unknownSc_throwsNotFound() {
    when(specialCommandService.getSpecialCommandById(scId))
        .thenThrow(new NotFoundException("SpecialCommand not found"));

    assertThrows(NotFoundException.class, () -> membershipService.listMembers(scId));
    verify(membershipRepository, never()).findAllByIdOrgUnitId(any());
  }

  // --- addMember ------------------------------------------------------------

  @Test
  void addMember_freshUser_persistsMembership() {
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(membershipRepository.existsByIdUserIdAndIdOrgUnitId(userId, scId)).thenReturn(false);
    when(membershipRepository.save(any(OrgUnitMembership.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    OrgUnitMembership saved = membershipService.addMember(scId, userId);

    assertSame(user, saved.getUser());
    assertEquals(OrgUnitKind.SPECIAL_COMMAND, saved.getKind());
    assertNotNull(saved.getJoinedAt());
    assertEquals(userId, saved.getId().getUserId());
    assertEquals(scId, saved.getId().getOrgUnitId());
    verify(membershipRepository).save(any(OrgUnitMembership.class));
  }

  @Test
  void addMember_alreadyMember_throwsDuplicate() {
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(membershipRepository.existsByIdUserIdAndIdOrgUnitId(userId, scId)).thenReturn(true);

    assertThrows(DuplicateEntityException.class, () -> membershipService.addMember(scId, userId));
    verify(membershipRepository, never()).save(any());
  }

  @Test
  void addMember_unknownUser_throwsNotFound() {
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> membershipService.addMember(scId, userId));
    verify(membershipRepository, never()).save(any());
  }

  @Test
  void addMember_unknownSc_throwsNotFound() {
    when(specialCommandService.getSpecialCommandById(scId))
        .thenThrow(new NotFoundException("SpecialCommand not found"));

    assertThrows(NotFoundException.class, () -> membershipService.addMember(scId, userId));
    verify(userRepository, never()).findById(any());
    verify(membershipRepository, never()).save(any());
  }

  // --- removeMember ---------------------------------------------------------

  @Test
  void removeMember_existing_deletes() {
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.existsById(id)).thenReturn(true);

    membershipService.removeMember(scId, userId);

    verify(membershipRepository).deleteById(id);
  }

  @Test
  void removeMember_nonMember_throwsNotFound() {
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.existsById(id)).thenReturn(false);

    assertThrows(NotFoundException.class, () -> membershipService.removeMember(scId, userId));
    verify(membershipRepository, never()).deleteById(any(OrgUnitMembershipId.class));
  }

  // --- patchFlags -----------------------------------------------------------

  @Test
  void patchFlags_bothFlagsSet_updatesBoth() {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setVersion(3L);
    m.setLogistician(false);
    m.setMissionManager(false);
    MembershipFlagsPatchRequest request = new MembershipFlagsPatchRequest(true, true, 3L);
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findById(id)).thenReturn(Optional.of(m));
    when(membershipRepository.save(m)).thenReturn(m);

    OrgUnitMembership updated = membershipService.patchFlags(scId, userId, request);

    assertTrue(updated.isLogistician());
    assertTrue(updated.isMissionManager());
  }

  @Test
  void patchFlags_onlyLogistician_leavesMissionManagerAlone() {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setVersion(0L);
    m.setLogistician(false);
    m.setMissionManager(true); // pre-existing true
    MembershipFlagsPatchRequest request = new MembershipFlagsPatchRequest(true, null, 0L);
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findById(id)).thenReturn(Optional.of(m));
    when(membershipRepository.save(m)).thenReturn(m);

    OrgUnitMembership updated = membershipService.patchFlags(scId, userId, request);

    assertTrue(updated.isLogistician());
    assertTrue(updated.isMissionManager(), "missionManager must stay true when not in payload");
  }

  @Test
  void patchFlags_staleVersion_throwsOptimisticLock() {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setVersion(5L);
    MembershipFlagsPatchRequest request = new MembershipFlagsPatchRequest(true, null, 0L);
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findById(id)).thenReturn(Optional.of(m));

    assertThrows(
        ObjectOptimisticLockingFailureException.class,
        () -> membershipService.patchFlags(scId, userId, request));
    verify(membershipRepository, never()).save(any());
  }

  @Test
  void patchFlags_unknownMembership_throwsNotFound() {
    MembershipFlagsPatchRequest request = new MembershipFlagsPatchRequest(true, null, 0L);
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class, () -> membershipService.patchFlags(scId, userId, request));
  }

  // --- toggleLead -----------------------------------------------------------

  @Test
  void toggleLead_promotes() {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setVersion(0L);
    m.setLead(false);
    MembershipLeadToggleRequest request = new MembershipLeadToggleRequest(true, 0L);
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findById(id)).thenReturn(Optional.of(m));
    when(membershipRepository.save(m)).thenReturn(m);

    OrgUnitMembership updated = membershipService.toggleLead(scId, userId, request);

    assertTrue(updated.isLead());
  }

  @Test
  void toggleLead_demotes() {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setVersion(2L);
    m.setLead(true);
    MembershipLeadToggleRequest request = new MembershipLeadToggleRequest(false, 2L);
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findById(id)).thenReturn(Optional.of(m));
    when(membershipRepository.save(m)).thenReturn(m);

    OrgUnitMembership updated = membershipService.toggleLead(scId, userId, request);

    assertFalse(updated.isLead());
  }

  @Test
  void toggleLead_staleVersion_throwsOptimisticLock() {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setVersion(5L);
    MembershipLeadToggleRequest request = new MembershipLeadToggleRequest(true, 0L);
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findById(id)).thenReturn(Optional.of(m));

    assertThrows(
        ObjectOptimisticLockingFailureException.class,
        () -> membershipService.toggleLead(scId, userId, request));
  }

  // --- listOptionsForUser ---------------------------------------------------

  @Test
  void listOptionsForUser_noMemberships_returnsEmptyListWithoutOrgUnitLookup() {
    when(membershipRepository.findAllByIdUserId(userId)).thenReturn(List.of());

    List<OrgUnitMembershipOptionDto> options = membershipService.listOptionsForUser(userId);

    assertTrue(options.isEmpty(), "no memberships → empty option list");
    verify(squadronRepository, never()).findById(any());
    verify(specialCommandRepository, never()).findById(any());
  }

  @Test
  void listOptionsForUser_singleStaffel_returnsOneOption() {
    UUID squadronId = UUID.randomUUID();
    OrgUnitMembership row = new OrgUnitMembership();
    row.setId(new OrgUnitMembershipId(userId, squadronId));
    row.setKind(OrgUnitKind.SQUADRON);
    when(membershipRepository.findAllByIdUserId(userId)).thenReturn(List.of(row));
    Squadron staffel = new Squadron();
    staffel.setId(squadronId);
    staffel.setName("IRIDIUM");
    staffel.setShorthand("IRI");
    when(squadronRepository.findById(squadronId)).thenReturn(Optional.of(staffel));

    List<OrgUnitMembershipOptionDto> options = membershipService.listOptionsForUser(userId);

    assertEquals(1, options.size());
    OrgUnitMembershipOptionDto only = options.getFirst();
    assertEquals(squadronId, only.orgUnitId());
    assertEquals("IRIDIUM", only.orgUnitName());
    assertEquals("IRI", only.orgUnitShorthand());
    assertEquals(OrgUnitKind.SQUADRON, only.kind());
  }

  @Test
  void listOptionsForUser_mixedKinds_sortsStaffelFirstThenSkAlphabetical() {
    UUID staffelId = UUID.randomUUID();
    UUID skBravoId = UUID.randomUUID();
    UUID skAlphaId = UUID.randomUUID();

    OrgUnitMembership rowSkBravo = new OrgUnitMembership();
    rowSkBravo.setId(new OrgUnitMembershipId(userId, skBravoId));
    rowSkBravo.setKind(OrgUnitKind.SPECIAL_COMMAND);
    OrgUnitMembership rowStaffel = new OrgUnitMembership();
    rowStaffel.setId(new OrgUnitMembershipId(userId, staffelId));
    rowStaffel.setKind(OrgUnitKind.SQUADRON);
    OrgUnitMembership rowSkAlpha = new OrgUnitMembership();
    rowSkAlpha.setId(new OrgUnitMembershipId(userId, skAlphaId));
    rowSkAlpha.setKind(OrgUnitKind.SPECIAL_COMMAND);
    when(membershipRepository.findAllByIdUserId(userId))
        .thenReturn(List.of(rowSkBravo, rowStaffel, rowSkAlpha));

    Squadron staffel = new Squadron();
    staffel.setId(staffelId);
    staffel.setName("IRIDIUM");
    staffel.setShorthand("IRI");
    when(squadronRepository.findById(staffelId)).thenReturn(Optional.of(staffel));

    SpecialCommand skBravo = new SpecialCommand();
    skBravo.setId(skBravoId);
    skBravo.setName("Bravo");
    skBravo.setShorthand("BRV");
    when(specialCommandRepository.findById(skBravoId)).thenReturn(Optional.of(skBravo));

    SpecialCommand skAlpha = new SpecialCommand();
    skAlpha.setId(skAlphaId);
    skAlpha.setName("Alpha");
    skAlpha.setShorthand("ALF");
    when(specialCommandRepository.findById(skAlphaId)).thenReturn(Optional.of(skAlpha));

    List<OrgUnitMembershipOptionDto> options = membershipService.listOptionsForUser(userId);

    assertEquals(3, options.size());
    assertEquals(OrgUnitKind.SQUADRON, options.get(0).kind());
    assertEquals("IRIDIUM", options.get(0).orgUnitName());
    assertEquals(OrgUnitKind.SPECIAL_COMMAND, options.get(1).kind());
    assertEquals("Alpha", options.get(1).orgUnitName());
    assertEquals(OrgUnitKind.SPECIAL_COMMAND, options.get(2).kind());
    assertEquals("Bravo", options.get(2).orgUnitName());
  }

  @Test
  void listOptionsForUser_orphanedRow_silentlyDropsTheMembership() {
    UUID orgUnitId = UUID.randomUUID();
    OrgUnitMembership row = new OrgUnitMembership();
    row.setId(new OrgUnitMembershipId(userId, orgUnitId));
    row.setKind(OrgUnitKind.SQUADRON);
    when(membershipRepository.findAllByIdUserId(userId)).thenReturn(List.of(row));
    when(squadronRepository.findById(orgUnitId)).thenReturn(Optional.empty());

    List<OrgUnitMembershipOptionDto> options = membershipService.listOptionsForUser(userId);

    assertTrue(
        options.isEmpty(),
        "membership row pointing at a deleted Squadron must not crash the picker");
  }

  // --- listAllActiveOptions (R5.d.c Job Order picker) -----------------------

  @Test
  void listAllActiveOptions_emptyCatalog_returnsEmptyList() {
    when(squadronRepository.findAllByActiveTrue()).thenReturn(List.of());
    when(specialCommandRepository.findAllByActiveTrue()).thenReturn(List.of());

    assertTrue(membershipService.listAllActiveOptions().isEmpty());
  }

  @Test
  void listAllActiveOptions_mixed_sortsStaffelFirstThenSkAlphabetical() {
    Squadron iri = new Squadron();
    iri.setId(UUID.randomUUID());
    iri.setName("IRIDIUM");
    iri.setShorthand("IRI");
    Squadron khg = new Squadron();
    khg.setId(UUID.randomUUID());
    khg.setName("KHG");
    khg.setShorthand("KHG");
    when(squadronRepository.findAllByActiveTrue()).thenReturn(List.of(iri, khg));

    SpecialCommand bravo = new SpecialCommand();
    bravo.setId(UUID.randomUUID());
    bravo.setName("Bravo");
    bravo.setShorthand("BRV");
    SpecialCommand alpha = new SpecialCommand();
    alpha.setId(UUID.randomUUID());
    alpha.setName("Alpha");
    alpha.setShorthand("ALF");
    when(specialCommandRepository.findAllByActiveTrue()).thenReturn(List.of(bravo, alpha));

    List<OrgUnitMembershipOptionDto> result = membershipService.listAllActiveOptions();

    assertEquals(4, result.size());
    // Staffeln first (alphabetical), then SKs (alphabetical).
    assertEquals(OrgUnitKind.SQUADRON, result.get(0).kind());
    assertEquals("IRIDIUM", result.get(0).orgUnitName());
    assertEquals(OrgUnitKind.SQUADRON, result.get(1).kind());
    assertEquals("KHG", result.get(1).orgUnitName());
    assertEquals(OrgUnitKind.SPECIAL_COMMAND, result.get(2).kind());
    assertEquals("Alpha", result.get(2).orgUnitName());
    assertEquals(OrgUnitKind.SPECIAL_COMMAND, result.get(3).kind());
    assertEquals("Bravo", result.get(3).orgUnitName());
  }
}
