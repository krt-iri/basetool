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

package de.greluc.krt.iri.basetool.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import de.greluc.krt.iri.basetool.backend.config.RefineryImportProperties;
import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.mapper.LocationMapper;
import de.greluc.krt.iri.basetool.backend.mapper.MaterialMapper;
import de.greluc.krt.iri.basetool.backend.mapper.RefiningMethodMapper;
import de.greluc.krt.iri.basetool.backend.mapper.UserMapper;
import de.greluc.krt.iri.basetool.backend.mapper.UserMapperImpl;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialExternalAlias;
import de.greluc.krt.iri.basetool.backend.model.MaterialExternalAliasSource;
import de.greluc.krt.iri.basetool.backend.model.MaterialType;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.RefiningMethod;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.ImportIssueCode;
import de.greluc.krt.iri.basetool.backend.model.dto.ImportIssueDto;
import de.greluc.krt.iri.basetool.backend.model.dto.ImportIssueSeverity;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryExtractDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryExtractGoodDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryExtractOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryImportDraftDto;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefiningMethodRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests of the refinery screenshot import draft building (#434): the §7.3 material-matching
 * stages, the §5 skip / un-quoted / checksum rules and the order-level field mapping — all against
 * mocked repositories with the real fuzzy matcher and real MapStruct mappers.
 */
@ExtendWith(MockitoExtension.class)
class RefineryImportServiceTest {

  private static final UUID CALLER_ID = UUID.randomUUID();

  @Mock private MaterialRepository materialRepository;
  @Mock private RefiningMethodRepository refiningMethodRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private UserRepository userRepository;
  @Mock private MaterialExternalAliasService aliasService;

  private RefineryImportService service;

  private Material stileron;
  private Material stileronRefined;
  private Material lindinium;
  private Material aluminum;
  private Material constructionSalvage;
  private Material quantainium;
  private Location levski;
  private RefiningMethod ferronExchange;

  @BeforeEach
  void setUp() {
    stileronRefined = material("Stileron", MaterialType.REFINED, false);
    stileron = material("Stileron (Raw)", MaterialType.RAW, false);
    stileron.setRefinedMaterial(stileronRefined);
    lindinium = material("Lindinium (Raw)", MaterialType.RAW, false);
    aluminum = material("Aluminum (Raw)", MaterialType.RAW, false);
    constructionSalvage = material("Construction Salvage", MaterialType.RAW, false);
    quantainium = material("Quantainium", MaterialType.NO_REFINE, true);
    lenient()
        .when(materialRepository.findRefineryInputCandidates(MaterialType.RAW))
        .thenReturn(List.of(stileron, lindinium, aluminum, constructionSalvage, quantainium));

    levski = new Location();
    levski.setId(UUID.randomUUID());
    levski.setName("Levski");
    lenient().when(locationRepository.findLocationsWithRefinery()).thenReturn(List.of(levski));

    ferronExchange = new RefiningMethod();
    ferronExchange.setId(UUID.randomUUID());
    ferronExchange.setName("Ferron Exchange");
    lenient()
        .when(refiningMethodRepository.findByNameIgnoreCase("FERRON EXCHANGE"))
        .thenReturn(Optional.of(ferronExchange));

    lenient()
        .when(
            aliasService.resolveMaterialByAlias(
                Mockito.any(MaterialExternalAliasSource.class), Mockito.anyString()))
        .thenReturn(null);

    User caller = new User();
    caller.setId(CALLER_ID);
    caller.setUsername("uploader");
    caller.setRank(10);
    lenient().when(userRepository.findById(CALLER_ID)).thenReturn(Optional.of(caller));

    UserMapper userMapper = new UserMapperImpl();
    ReflectionTestUtils.setField(
        userMapper, "membershipRepository", Mockito.mock(OrgUnitMembershipRepository.class));
    ReflectionTestUtils.setField(
        userMapper, "squadronRepository", Mockito.mock(SquadronRepository.class));

    service =
        new RefineryImportService(
            materialRepository,
            refiningMethodRepository,
            locationRepository,
            userRepository,
            aliasService,
            new BlueprintFuzzyMatcher(),
            new RefineryImportProperties(),
            Mappers.getMapper(MaterialMapper.class),
            Mappers.getMapper(LocationMapper.class),
            Mappers.getMapper(RefiningMethodMapper.class),
            userMapper);
  }

  // ─── envelope validation ──────────────────────────────────────────────────

  @Test
  void buildDraft_rejectsUnsupportedSchemaVersion() {
    // Given
    RefineryExtractDto extract = extract(2, setupOrder(List.of(quotedGood(0, "STILERON (ORE)"))));

    // When / Then
    assertThatThrownBy(() -> service.buildDraft(extract, CALLER_ID))
        .isInstanceOf(BadRequestException.class)
        .hasMessage("error.refineryImport.unsupportedSchemaVersion");
  }

  @Test
  void buildDraft_rejectsProcessingPanel() {
    // Given
    RefineryExtractOrderDto order =
        new RefineryExtractOrderDto(
            "PROCESSING", true, 0.9, null, null, null, null, null, null, null, null, List.of());
    RefineryExtractDto extract = extract(1, order);

    // When / Then
    assertThatThrownBy(() -> service.buildDraft(extract, CALLER_ID))
        .isInstanceOf(BadRequestException.class)
        .hasMessage("error.refineryImport.unsupportedPanelType");
  }

  @Test
  void buildDraft_flagsMultipleOrdersAsTruncatedInfo() {
    // Given
    RefineryExtractOrderDto first = setupOrder(List.of(quotedGood(0, "STILERON (ORE)")));
    RefineryExtractOrderDto second = setupOrder(List.of(quotedGood(0, "LINDINIUM (ORE)")));

    // When
    RefineryImportDraftDto draft = service.buildDraft(extract(1, first, second), CALLER_ID);

    // Then
    ImportIssueDto issue = onlyIssue(draft, ImportIssueCode.MULTIPLE_ORDERS_TRUNCATED);
    assertThat(issue.severity()).isEqualTo(ImportIssueSeverity.INFO);
    assertThat(draft.order().goods()).hasSize(1);
    assertThat(draft.order().goods().getFirst().inputMaterial().name()).isEqualTo("Stileron (Raw)");
  }

  // ─── material matching stages (§7.3) ──────────────────────────────────────

  @Test
  void buildDraft_matchesOreSuffixAgainstRawSuffixViaCanonicalFold() {
    // Given — master data says "Stileron (Raw)", the screen says "STILERON (ORE)"
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "STILERON (ORE)"));

    // Then
    assertThat(draft.goodsMatched()).isEqualTo(1);
    assertThat(draft.order().goods().getFirst().inputMaterial().id()).isEqualTo(stileron.getId());
    assertThat(issues(draft, ImportIssueCode.UNMATCHED_MATERIAL)).isEmpty();
    assertThat(issues(draft, ImportIssueCode.LOW_CONFIDENCE_MATERIAL)).isEmpty();
  }

  @Test
  void buildDraft_matchesCaseInsensitively() {
    // Given
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "stileron (raw)"));

    // Then
    assertThat(draft.goodsMatched()).isEqualTo(1);
  }

  @Test
  void buildDraft_matchesViaRefineryScreenAlias() {
    // Given — a name no canonical fold can place, but an admin curated an alias for it
    lenient()
        .when(
            aliasService.resolveMaterialByAlias(
                MaterialExternalAliasSource.REFINERY_SCREEN, "SHINY ROCKS"))
        .thenReturn(lindinium);

    // When
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "SHINY ROCKS"));

    // Then
    assertThat(draft.goodsMatched()).isEqualTo(1);
    assertThat(draft.order().goods().getFirst().inputMaterial().id()).isEqualTo(lindinium.getId());
    verify(aliasService)
        .resolveMaterialByAlias(MaterialExternalAliasSource.REFINERY_SCREEN, "SHINY ROCKS");
  }

  @Test
  void buildDraft_ignoresAliasTargetingNonCandidateMaterial() {
    // Given — an admin mis-curated an alias onto a REFINED material the create path rejects
    lenient()
        .when(
            aliasService.resolveMaterialByAlias(
                MaterialExternalAliasSource.REFINERY_SCREEN, "WEIRD STUFF"))
        .thenReturn(stileronRefined);

    // When
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "WEIRD STUFF"));

    // Then — the alias stage must not bypass the candidate gate; the row stays unmatched
    assertThat(draft.goodsMatched()).isZero();
    assertThat(draft.order().goods().getFirst().inputMaterial()).isNull();
    assertThat(issues(draft, ImportIssueCode.UNMATCHED_MATERIAL)).hasSize(1);
  }

  @Test
  void buildDraft_matchesGameUiTruncatedNameViaUniqueSuffix() {
    // Given — the game UI clipped "…Construction Salvage" to "UCTION SALVAGE" (golden set)
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "UCTION SALVAGE"));

    // Then
    assertThat(draft.goodsMatched()).isEqualTo(1);
    assertThat(draft.order().goods().getFirst().inputMaterial().id())
        .isEqualTo(constructionSalvage.getId());
  }

  @Test
  void buildDraft_matchesTruncatedNameViaAliasContainmentAnchor() {
    // Given — the catalogue names it "Construction Material Salvage" (UEX spelling), the game UI
    // shows "Construction Salvage" and clips it to "UCTION SALVAGE"; the master name does not
    // contain the fragment, but one curated alias of the on-screen spelling does
    Material constructionMaterialSalvage =
        material("Construction Material Salvage", MaterialType.RAW, false);
    lenient()
        .when(materialRepository.findRefineryInputCandidates(MaterialType.RAW))
        .thenReturn(List.of(stileron, lindinium, aluminum, constructionMaterialSalvage));
    lenient()
        .when(aliasService.findBySourceSystem(MaterialExternalAliasSource.REFINERY_SCREEN))
        .thenReturn(List.of(alias("CONSTRUCTION SALVAGE", constructionMaterialSalvage)));

    // When
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "UCTION SALVAGE"));

    // Then — a deterministic hit, not a fuzzy one
    assertThat(draft.goodsMatched()).isEqualTo(1);
    assertThat(draft.order().goods().getFirst().inputMaterial().id())
        .isEqualTo(constructionMaterialSalvage.getId());
    assertThat(issues(draft, ImportIssueCode.UNMATCHED_MATERIAL)).isEmpty();
    assertThat(issues(draft, ImportIssueCode.LOW_CONFIDENCE_MATERIAL)).isEmpty();
  }

  @Test
  void buildDraft_matchesBothSideTruncatedNameViaAliasContainmentAnchor() {
    // Given — clipping on both ends still yields a contiguous fragment of the alias name
    Material constructionMaterialSalvage =
        material("Construction Material Salvage", MaterialType.RAW, false);
    lenient()
        .when(materialRepository.findRefineryInputCandidates(MaterialType.RAW))
        .thenReturn(List.of(stileron, lindinium, aluminum, constructionMaterialSalvage));
    lenient()
        .when(aliasService.findBySourceSystem(MaterialExternalAliasSource.REFINERY_SCREEN))
        .thenReturn(List.of(alias("CONSTRUCTION SALVAGE", constructionMaterialSalvage)));

    // When
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "UCTION SALV"));

    // Then
    assertThat(draft.goodsMatched()).isEqualTo(1);
    assertThat(draft.order().goods().getFirst().inputMaterial().id())
        .isEqualTo(constructionMaterialSalvage.getId());
  }

  @Test
  void buildDraft_countsNameAndAliasAnchorOfSameMaterialAsOneHit() {
    // Given — the fragment is contained in the candidate's own name AND in an alias pointing at
    // the same material; uniqueness is judged per material, so this stays a single hit
    lenient()
        .when(aliasService.findBySourceSystem(MaterialExternalAliasSource.REFINERY_SCREEN))
        .thenReturn(List.of(alias("CONSTRUCTION SALVAGE", constructionSalvage)));

    // When
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "UCTION SALVAGE"));

    // Then
    assertThat(draft.goodsMatched()).isEqualTo(1);
    assertThat(draft.order().goods().getFirst().inputMaterial().id())
        .isEqualTo(constructionSalvage.getId());
  }

  @Test
  void buildDraft_leavesAmbiguousTruncationAcrossNameAndAliasAnchorsUnmatched() {
    // Given — the fragment hits the candidate "Construction Salvage" directly AND an alias
    // pointing at a different material; the union has two materials, so no deterministic match
    Material constructionMaterialSalvage =
        material("Construction Material Salvage", MaterialType.RAW, false);
    lenient()
        .when(materialRepository.findRefineryInputCandidates(MaterialType.RAW))
        .thenReturn(
            List.of(
                stileron, lindinium, aluminum, constructionSalvage, constructionMaterialSalvage));
    lenient()
        .when(aliasService.findBySourceSystem(MaterialExternalAliasSource.REFINERY_SCREEN))
        .thenReturn(List.of(alias("CONSTRUCTION SALVAGE", constructionMaterialSalvage)));

    // When
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "UCTION SALVAGE"));

    // Then — falls through to fuzzy, which stays below the accept threshold here
    assertThat(draft.goodsMatched()).isZero();
    assertThat(draft.order().goods().getFirst().inputMaterial()).isNull();
    assertThat(issues(draft, ImportIssueCode.UNMATCHED_MATERIAL)).hasSize(1);
  }

  @Test
  void buildDraft_ignoresAliasAnchorTargetingNonCandidateMaterial() {
    // Given — the only containment anchor is an alias mis-curated onto a REFINED material; the
    // truncation stage must honour the create-path gate just like the exact-alias stage
    lenient()
        .when(materialRepository.findRefineryInputCandidates(MaterialType.RAW))
        .thenReturn(List.of(stileron, lindinium, aluminum));
    lenient()
        .when(aliasService.findBySourceSystem(MaterialExternalAliasSource.REFINERY_SCREEN))
        .thenReturn(List.of(alias("CONSTRUCTION SALVAGE", stileronRefined)));

    // When
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "UCTION SALVAGE"));

    // Then
    assertThat(draft.goodsMatched()).isZero();
    assertThat(draft.order().goods().getFirst().inputMaterial()).isNull();
    assertThat(issues(draft, ImportIssueCode.UNMATCHED_MATERIAL)).hasSize(1);
  }

  @Test
  void buildDraft_matchesManualRawMaterialCandidate() {
    // Given — isManualRawMaterial materials pass the create-path gate even when not type RAW
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "QUANTAINIUM"));

    // Then
    assertThat(draft.goodsMatched()).isEqualTo(1);
    assertThat(draft.order().goods().getFirst().inputMaterial().id())
        .isEqualTo(quantainium.getId());
    verify(materialRepository).findRefineryInputCandidates(MaterialType.RAW);
  }

  @Test
  void buildDraft_acceptsFuzzyMatchAboveThresholdButFlagsIt() {
    // Given — one-character drift on a ten-character name scores exactly 0.9
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "LINDINIUMM (ORE)"));

    // Then
    assertThat(draft.goodsMatched()).isEqualTo(1);
    assertThat(draft.order().goods().getFirst().inputMaterial().id()).isEqualTo(lindinium.getId());
    ImportIssueDto issue = onlyIssue(draft, ImportIssueCode.LOW_CONFIDENCE_MATERIAL);
    assertThat(issue.field()).isEqualTo("goods[0].inputMaterial");
    assertThat(issue.confidence()).isEqualTo(0.9);
    assertThat(issue.suggestions()).isNotEmpty();
    assertThat(issue.suggestions().getFirst().id()).isEqualTo(lindinium.getId());
  }

  @Test
  void buildDraft_leavesFuzzyBelowThresholdUnmatchedWithSuggestions() {
    // Given — "ALUMINIUM" vs "Aluminum" scores ~0.889, below the 0.9 accept threshold
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "ALUMINIUM (ORE)"));

    // Then
    assertThat(draft.goodsMatched()).isZero();
    assertThat(draft.order().goods().getFirst().inputMaterial()).isNull();
    ImportIssueDto issue = onlyIssue(draft, ImportIssueCode.UNMATCHED_MATERIAL);
    assertThat(issue.severity()).isEqualTo(ImportIssueSeverity.WARNING);
    assertThat(issue.rawValue()).isEqualTo("ALUMINIUM (ORE)");
    assertThat(issue.suggestions()).isNotEmpty();
    assertThat(issue.suggestions().getFirst().id()).isEqualTo(aluminum.getId());
  }

  @Test
  void buildDraft_leavesGarbageUnmatchedWithoutSuggestions() {
    // Given
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "XQZWV"));

    // Then
    assertThat(draft.goodsMatched()).isZero();
    ImportIssueDto issue = onlyIssue(draft, ImportIssueCode.UNMATCHED_MATERIAL);
    assertThat(issue.suggestions()).isNull();
  }

  @Test
  void buildDraft_preservesDuplicateMaterialRowsInRowIndexOrder() {
    // Given — 4× LINDINIUM at different qualities is a normal real-world order (golden set)
    RefineryImportDraftDto draft =
        draftFor(
            good(3, "LINDINIUM (ORE)", 729, 200, 90, true),
            good(0, "LINDINIUM (ORE)", 385, 957, 448, true),
            good(2, "LINDINIUM (ORE)", 618, 500, 230, true),
            good(1, "LINDINIUM (ORE)", 585, 300, 140, true));

    // Then
    assertThat(draft.goodsMatched()).isEqualTo(4);
    assertThat(draft.order().goods()).hasSize(4);
    assertThat(draft.order().goods().stream().map(g -> g.quality()).toList())
        .containsExactly(385, 585, 618, 729);
    assertThat(draft.order().goods())
        .allSatisfy(g -> assertThat(g.inputMaterial().id()).isEqualTo(lindinium.getId()));
  }

  // ─── skip rules & un-quoted handling (§5) ─────────────────────────────────

  @Test
  void buildDraft_skipsRefineOffRowAsInfo() {
    // Given — INERT MATERIALS row: refine OFF
    RefineryImportDraftDto draft =
        draftFor(quotedGood(0, "STILERON (ORE)"), good(1, "INERT MATERIALS", 0, 5449, 0, false));

    // Then
    assertThat(draft.order().goods()).hasSize(1);
    assertThat(draft.rowsSkipped()).isEqualTo(1);
    assertThat(draft.goodsTotal()).isEqualTo(2);
    ImportIssueDto issue = onlyIssue(draft, ImportIssueCode.SKIPPED_REFINE_OFF);
    assertThat(issue.severity()).isEqualTo(ImportIssueSeverity.INFO);
    assertThat(issue.field()).isEqualTo("goods[1]");
  }

  @Test
  void buildDraft_skipsZeroQuantityRowAsWarning() {
    // Given
    RefineryImportDraftDto draft = draftFor(good(0, "STILERON (ORE)", 618, 0, 5, true));

    // Then
    assertThat(draft.order().goods()).isEmpty();
    ImportIssueDto issue = onlyIssue(draft, ImportIssueCode.SKIPPED_ZERO_QTY);
    assertThat(issue.severity()).isEqualTo(ImportIssueSeverity.WARNING);
  }

  @Test
  void buildDraft_reportsUnquotedRowDistinctFromZeroQty() {
    // Given — YIELD read as "--" (null) on one row, the other is quoted
    RefineryImportDraftDto draft =
        draftFor(good(0, "STILERON (ORE)", 618, 957, null, true), quotedGood(1, "LINDINIUM (ORE)"));

    // Then
    assertThat(draft.order().goods()).hasSize(1);
    ImportIssueDto issue = onlyIssue(draft, ImportIssueCode.UNQUOTED_ROW);
    assertThat(issue.severity()).isEqualTo(ImportIssueSeverity.WARNING);
    assertThat(issues(draft, ImportIssueCode.SKIPPED_ZERO_QTY)).isEmpty();
    assertThat(issues(draft, ImportIssueCode.UNQUOTED_ORDER)).isEmpty();
  }

  @Test
  void buildDraft_flagsAllUnquotedRowsAsBlockingUnquotedOrder() {
    // Given — every YIELD cell is "--": captured before GET QUOTE
    RefineryImportDraftDto draft =
        draftFor(
            good(0, "STILERON (ORE)", 618, 957, null, true),
            good(1, "LINDINIUM (ORE)", 385, 300, null, true));

    // Then
    ImportIssueDto issue = onlyIssue(draft, ImportIssueCode.UNQUOTED_ORDER);
    assertThat(issue.severity()).isEqualTo(ImportIssueSeverity.BLOCKING);
    assertThat(draft.order().goods()).isEmpty();
    assertThat(draft.rowsSkipped()).isEqualTo(2);
  }

  @Test
  void buildDraft_honoursProducerQuotedFlagForUnquotedOrder() {
    // Given — the producer marked the capture un-quoted even though rows carry numbers
    RefineryExtractOrderDto order =
        new RefineryExtractOrderDto(
            "SETUP",
            false,
            0.9,
            "LEVSKI",
            "FERRON EXCHANGE",
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(quotedGood(0, "STILERON (ORE)")));

    // When
    RefineryImportDraftDto draft = service.buildDraft(extract(1, order), CALLER_ID);

    // Then
    assertThat(issues(draft, ImportIssueCode.UNQUOTED_ORDER)).hasSize(1);
  }

  // ─── header-total checksum (§5) ───────────────────────────────────────────

  @Test
  void buildDraft_flagsHeaderTotalMismatchOnBothTotals() {
    // Given — IN MANIFEST counts all rows, TO REFINE only refine-ON rows (v1 hypothesis)
    RefineryExtractOrderDto order =
        new RefineryExtractOrderDto(
            "SETUP",
            true,
            0.9,
            "LEVSKI",
            "FERRON EXCHANGE",
            9999L,
            8888L,
            null,
            null,
            null,
            null,
            List.of(
                quotedGood(0, "STILERON (ORE)"), good(1, "INERT MATERIALS", 0, 5449, 0, false)));

    // When
    RefineryImportDraftDto draft = service.buildDraft(extract(1, order), CALLER_ID);

    // Then
    List<ImportIssueDto> mismatches = issues(draft, ImportIssueCode.SUM_MISMATCH);
    assertThat(mismatches).hasSize(2);
    assertThat(mismatches.stream().map(ImportIssueDto::field))
        .containsExactlyInAnyOrder("rawInManifestTotal", "rawToRefineTotal");
  }

  @Test
  void buildDraft_acceptsReconcilingHeaderTotals() {
    // Given — quoted row qty 957 + inert qty 5449: IN MANIFEST = 6406, TO REFINE = 957
    RefineryExtractOrderDto order =
        new RefineryExtractOrderDto(
            "SETUP",
            true,
            0.9,
            "LEVSKI",
            "FERRON EXCHANGE",
            6406L,
            957L,
            null,
            null,
            null,
            null,
            List.of(
                quotedGood(0, "STILERON (ORE)"), good(1, "INERT MATERIALS", 0, 5449, 0, false)));

    // When
    RefineryImportDraftDto draft = service.buildDraft(extract(1, order), CALLER_ID);

    // Then
    assertThat(issues(draft, ImportIssueCode.SUM_MISMATCH)).isEmpty();
  }

  // ─── per-good field mapping (§7.2) ────────────────────────────────────────

  @Test
  void buildDraft_keepsOutOfRangeQualityButWarns() {
    // Given
    RefineryImportDraftDto draft = draftFor(good(0, "STILERON (ORE)", 1500, 957, 448, true));

    // Then
    assertThat(draft.order().goods().getFirst().quality()).isEqualTo(1500);
    ImportIssueDto issue = onlyIssue(draft, ImportIssueCode.OUT_OF_RANGE_QUALITY);
    assertThat(issue.field()).isEqualTo("goods[0].quality");
  }

  @Test
  void buildDraft_defaultsNullQualityToZero() {
    // Given
    RefineryImportDraftDto draft = draftFor(good(0, "STILERON (ORE)", null, 957, 448, true));

    // Then
    assertThat(draft.order().goods().getFirst().quality()).isZero();
    assertThat(issues(draft, ImportIssueCode.OUT_OF_RANGE_QUALITY)).isEmpty();
  }

  @Test
  void buildDraft_derivesOutputMaterialFromRefinedMaterialLink() {
    // Given
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "STILERON (ORE)"));

    // Then
    assertThat(draft.order().goods().getFirst().outputMaterial().id())
        .isEqualTo(stileronRefined.getId());
    assertThat(issues(draft, ImportIssueCode.NO_REFINED_MATERIAL)).isEmpty();
  }

  @Test
  void buildDraft_reportsMissingRefinedMaterialLinkAsInfo() {
    // Given — Lindinium has no admin-curated refinedMaterial
    RefineryImportDraftDto draft = draftFor(quotedGood(0, "LINDINIUM (ORE)"));

    // Then
    assertThat(draft.order().goods().getFirst().outputMaterial()).isNull();
    ImportIssueDto issue = onlyIssue(draft, ImportIssueCode.NO_REFINED_MATERIAL);
    assertThat(issue.severity()).isEqualTo(ImportIssueSeverity.INFO);
    assertThat(issue.field()).isEqualTo("goods[0].outputMaterial");
    // row-level issues carry the row's derived read confidence (REQ-REFINERY-009)
    assertThat(issue.confidence()).isEqualTo(0.95);
  }

  // ─── order-level mapping (§7.1) ───────────────────────────────────────────

  @Test
  void buildDraft_mapsOrderLevelFieldsAndDefaults() {
    // Given
    RefineryExtractOrderDto order =
        new RefineryExtractOrderDto(
            "SETUP",
            true,
            0.92,
            "LEVSKI",
            "FERRON EXCHANGE",
            null,
            null,
            48928.0,
            1258L,
            null,
            null,
            List.of(quotedGood(0, "STILERON (ORE)")));

    // When
    RefineryImportDraftDto draft = service.buildDraft(extract(1, order), CALLER_ID);

    // Then
    assertThat(draft.order().location().id()).isEqualTo(levski.getId());
    assertThat(draft.order().refiningMethod().id()).isEqualTo(ferronExchange.getId());
    assertThat(draft.order().expenses()).isEqualTo(48928.0);
    assertThat(draft.order().durationMinutes()).isEqualTo(1258L);
    assertThat(draft.order().status()).isEqualTo(RefineryOrderStatus.OPEN.name());
    assertThat(draft.order().owner().id()).isEqualTo(CALLER_ID);
    assertThat(draft.order().mission()).isNull();
    assertThat(draft.order().startedAt()).isNull();
    assertThat(issues(draft, ImportIssueCode.UNRESOLVED_LOCATION)).isEmpty();
    assertThat(issues(draft, ImportIssueCode.UNRESOLVED_METHOD)).isEmpty();
  }

  @Test
  void buildDraft_flagsMissingLocationAndMethodForPreCroppedInput() {
    // Given — pre-cropped panel input never contains the terminal header
    RefineryExtractOrderDto order = setupOrder(List.of(quotedGood(0, "STILERON (ORE)")));

    // When
    RefineryImportDraftDto draft = service.buildDraft(extract(1, order), CALLER_ID);

    // Then
    assertThat(draft.order().location()).isNull();
    assertThat(draft.order().refiningMethod()).isNull();
    assertThat(onlyIssue(draft, ImportIssueCode.UNRESOLVED_LOCATION).severity())
        .isEqualTo(ImportIssueSeverity.WARNING);
    assertThat(onlyIssue(draft, ImportIssueCode.UNRESOLVED_METHOD).severity())
        .isEqualTo(ImportIssueSeverity.WARNING);
  }

  @Test
  void buildDraft_leavesOwnerNullWhenCallerUnknown() {
    // Given
    UUID strangerId = UUID.randomUUID();
    lenient().when(userRepository.findById(strangerId)).thenReturn(Optional.empty());

    // When
    RefineryImportDraftDto draft =
        service.buildDraft(
            extract(1, setupOrder(List.of(quotedGood(0, "STILERON (ORE)")))), strangerId);

    // Then
    assertThat(draft.order().owner()).isNull();
  }

  // ─── standalone matchers ──────────────────────────────────────────────────

  @Test
  void matchMethod_isCaseInsensitiveAndNullSafe() {
    assertThat(service.matchMethod("FERRON EXCHANGE")).contains(ferronExchange);
    assertThat(service.matchMethod(null)).isEmpty();
    assertThat(service.matchMethod("  ")).isEmpty();
  }

  @Test
  void matchRefineryLocation_matchesByCanonicalFold() {
    assertThat(service.matchRefineryLocation("LEVSKI")).contains(levski);
    assertThat(service.matchRefineryLocation("Orison")).isEmpty();
    assertThat(service.matchRefineryLocation(null)).isEmpty();
  }

  @Test
  void matchMaterial_exposesTheMatchingChain() {
    assertThat(service.matchMaterial("STILERON (ORE)")).contains(stileron);
    assertThat(service.matchMaterial("XQZWV")).isEmpty();
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private RefineryImportDraftDto draftFor(RefineryExtractGoodDto... goods) {
    return service.buildDraft(extract(1, setupOrder(List.of(goods))), CALLER_ID);
  }

  private static RefineryExtractDto extract(int schemaVersion, RefineryExtractOrderDto... orders) {
    return new RefineryExtractDto(
        schemaVersion,
        "basetool-sc-extractor",
        "1.0.0",
        "qwen3-vl:8b-instruct",
        Instant.parse("2026-06-05T20:00:00Z"),
        "en",
        List.of(orders));
  }

  private static RefineryExtractOrderDto setupOrder(List<RefineryExtractGoodDto> goods) {
    return new RefineryExtractOrderDto(
        "SETUP", true, 0.92, null, null, null, null, null, null, null, null, goods);
  }

  private static RefineryExtractGoodDto quotedGood(int rowIndex, String rawMaterialName) {
    return good(rowIndex, rawMaterialName, 618, 957, 448, true);
  }

  private static RefineryExtractGoodDto good(
      Integer rowIndex,
      String rawMaterialName,
      Integer quality,
      Integer inputQuantity,
      Integer outputQuantity,
      boolean refine) {
    return new RefineryExtractGoodDto(
        rowIndex, rawMaterialName, quality, inputQuantity, outputQuantity, refine, 0.95, null);
  }

  private static MaterialExternalAlias alias(String externalName, Material target) {
    MaterialExternalAlias alias = new MaterialExternalAlias();
    alias.setId(UUID.randomUUID());
    alias.setSourceSystem(MaterialExternalAliasSource.REFINERY_SCREEN);
    alias.setExternalName(externalName);
    alias.setMaterial(target);
    return alias;
  }

  private static Material material(String name, MaterialType type, boolean manualRaw) {
    Material material = new Material();
    material.setId(UUID.randomUUID());
    material.setName(name);
    material.setType(type);
    material.setIsManualRawMaterial(manualRaw);
    material.setIsVisible(true);
    return material;
  }

  private static List<ImportIssueDto> issues(RefineryImportDraftDto draft, ImportIssueCode code) {
    return draft.issues().stream().filter(i -> i.code() == code).toList();
  }

  private static ImportIssueDto onlyIssue(RefineryImportDraftDto draft, ImportIssueCode code) {
    List<ImportIssueDto> matching = issues(draft, code);
    assertThat(matching).hasSize(1);
    return matching.getFirst();
  }
}
