package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.mapper.BlueprintMapper;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintDto;
import de.greluc.krt.iri.basetool.backend.model.scwiki.Blueprint;
import de.greluc.krt.iri.basetool.backend.repository.BlueprintRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Unit tests for {@link BlueprintService} filter normalisation and DTO mapping. */
@ExtendWith(MockitoExtension.class)
class BlueprintServiceTest {

  @Mock private BlueprintRepository blueprintRepository;
  @Mock private BlueprintMapper blueprintMapper;
  @InjectMocks private BlueprintService blueprintService;

  @Test
  void getBlueprints_blankSearch_usesUnfilteredQueryAndMaps() {
    Blueprint bp = new Blueprint();
    when(blueprintRepository.findByScwikiDeletedAtIsNull(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(bp)));
    when(blueprintMapper.toDto(bp)).thenReturn(minimalDto());

    Page<BlueprintDto> result = blueprintService.getBlueprints("   ", PageRequest.of(0, 10));

    assertEquals(1, result.getContent().size());
    verify(blueprintRepository).findByScwikiDeletedAtIsNull(any(Pageable.class));
  }

  @Test
  void getBlueprints_nullSearch_usesUnfilteredQuery() {
    when(blueprintRepository.findByScwikiDeletedAtIsNull(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    blueprintService.getBlueprints(null, PageRequest.of(0, 10));

    verify(blueprintRepository).findByScwikiDeletedAtIsNull(any(Pageable.class));
  }

  @Test
  void getBlueprints_trimsSearchBeforeQuerying() {
    when(blueprintRepository.searchActive(eq("omni"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    blueprintService.getBlueprints("  omni  ", PageRequest.of(0, 10));

    verify(blueprintRepository).searchActive(eq("omni"), any(Pageable.class));
  }

  private static BlueprintDto minimalDto() {
    return new BlueprintDto(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "BP",
        "Omnisky",
        540,
        false,
        2,
        1,
        "4.8",
        15,
        0.5,
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        0L);
  }
}
